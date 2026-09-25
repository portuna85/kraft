package com.kraft.user.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 회원 탈퇴를 실제 DB로 검증한다.
 * <p>
 * 이 기능에서 값이 맞는지보다 중요한 것은 <b>탈퇴 뒤에 무엇이 남는가</b>이다. 글은 남아야 하고
 * (남의 댓글이 달린 대화가 통째로 사라지면 안 된다), 그 사람을 가리키는 값은 남으면 안 되며,
 * 원래 주소는 다시 쓸 수 있어야 한다. 단위 테스트는 호출만 보므로 이 셋을 증명하지 못한다.
 */
@SpringBootTest
class WithdrawalFlowTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    private User user;
    private String email;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        email = "bye-" + unique + "@example.com";
        user = userRepository.save(User.builder()
                .name("bye-" + unique)
                .email(email)
                .password(passwordEncoder.encode("OldPass1!"))
                .role(Role.USER)
                .build());
    }

    @Test
    @DisplayName("탈퇴해도 쓴 글은 남고 작성자만 익명으로 바뀐다")
    void withdraw_keepsPostsAndAnonymizesAuthor() {
        Post post = postRepository.save(Post.builder()
                .title("탈퇴해도 남을 글")
                .content("내용")
                .user(user)
                .build());

        userService.withdraw(email, "OldPass1!");

        Post reloaded = postRepository.findById(post.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("탈퇴해도 남을 글");
        // 작성자 연결은 그대로 남고, 그 회원의 이름만 익명으로 바뀐다. (OSIV가 꺼져 있어
        // 트랜잭션 밖에서 post.getUser()를 따라가면 프록시를 초기화할 수 없으므로 id로 읽는다.)
        User author = userRepository.findById(user.getId()).orElseThrow();
        assertThat(author.getName()).isEqualTo("탈퇴한 사용자" + user.getId());
        assertThat(author.isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("탈퇴하면 그 계정으로는 로그인할 수 없다")
    void withdraw_blocksLogin() {
        userService.withdraw(email, "OldPass1!");

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("탈퇴한 주소로 다시 가입할 수 있다 — 탈퇴가 그 주소를 영영 잠그면 안 된다")
    void withdraw_freesTheEmailForSignUpAgain() {
        userService.withdraw(email, "OldPass1!");

        Long newId = userService.signUp("다시온사람-" + UUID.randomUUID().toString().substring(0, 6),
                email, "BrandNew1!");

        User rejoined = userRepository.findById(newId).orElseThrow();
        assertThat(rejoined.getId()).isNotEqualTo(user.getId());
        assertThat(rejoined.isWithdrawn()).isFalse();
        // 옛 계정은 그대로 탈퇴 상태다. 두 행이 같은 주소를 가리키지 않는다.
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail())
                .isNotEqualTo(email);
    }

    /**
     * 평가 보고서 2026-09-25 F10. 탈퇴 대체 이름은 "탈퇴한 사용자{id}"로 예측 가능하다. 예약어
     * 가입 제한이 생기기 전에 누군가 그 이름으로 가입해 두었다면(기존 데이터), 예전에는 이름
     * 유일성 제약에 걸려 탈퇴 자체가 실패했다.
     */
    @Test
    @DisplayName("F10: 다른 계정이 대체 이름을 이미 쓰고 있어도 탈퇴된다")
    void withdraw_whenReplacementNameTaken_stillSucceeds() {
        String replacementName = "탈퇴한 사용자" + user.getId();
        User squatter = userRepository.save(User.builder()
                .name(replacementName)
                .email("squat-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .password("x")
                .role(Role.USER)
                .build());

        userService.withdraw(email, "OldPass1!");

        User withdrawn = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getName()).startsWith(replacementName).isNotEqualTo(replacementName);
        assertThat(withdrawn.getName().length()).isLessThanOrEqualTo(50);
        assertThat(withdrawn.getEmail()).isNotEqualTo(email);
        assertThat(userRepository.findById(squatter.getId()).orElseThrow().getName()).isEqualTo(replacementName);
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("F10: 다른 계정이 대체 이메일을 이미 쓰고 있어도 탈퇴된다")
    void withdraw_whenReplacementEmailTaken_stillSucceeds() {
        String replacementEmail = "withdrawn-" + user.getId() + "@kraft.invalid";
        User squatter = userRepository.save(User.builder()
                .name("squat-" + UUID.randomUUID().toString().substring(0, 8))
                .email(replacementEmail)
                .password("x")
                .role(Role.USER)
                .build());

        userService.withdraw(email, "OldPass1!");

        User withdrawn = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getEmail()).endsWith("@kraft.invalid").isNotEqualTo(replacementEmail);
        assertThat(userRepository.findById(squatter.getId()).orElseThrow().getEmail()).isEqualTo(replacementEmail);
        // 원래 주소는 여전히 다시 가입할 수 있다.
        assertThat(userService.signUp("재가입-" + UUID.randomUUID().toString().substring(0, 6), email, "BrandNew1!"))
                .isNotNull();
    }

    @Test
    @DisplayName("F10: 탈퇴 대체 이름·이메일 공간은 새 가입에 쓸 수 없다")
    void signUp_withReservedNameOrEmail_isRejected() {
        assertThatThrownBy(() -> userService.signUp("탈퇴한 사용자999999", "fresh-" + UUID.randomUUID() + "@example.com", "BrandNew1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용할 수 없는 이름");
        assertThatThrownBy(() -> userService.signUp("새사람-" + UUID.randomUUID().toString().substring(0, 6), "withdrawn-999999@KRAFT.invalid", "BrandNew1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용할 수 없는 이메일");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 탈퇴하지 않는다 — 계정은 그대로 남는다")
    void withdraw_withWrongPassword_keepsAccountIntact() {
        assertThatThrownBy(() -> userService.withdraw(email, "WrongPass1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isWithdrawn()).isFalse();
        assertThat(reloaded.getEmail()).isEqualTo(email);
        assertThat(userDetailsService.loadUserByUsername(email)).isNotNull();
    }
}
