package com.kraft.e2e;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * E2E가 기대는 고정 데이터를 만든다. H2 인메모리 + {@code create-drop}이므로 매 기동마다
 * 같은 상태에서 시작한다.
 * <p>
 * 이 시더가 필요한 이유는 <b>이메일 인증 때문</b>이다. 회원가입으로 만든 계정은 GUEST라
 * 글을 쓸 수 없고, USER로 올리려면 메일로 받은 링크를 눌러야 한다. 대부분의 시나리오는
 * "이미 인증을 마친 사용자"에서 시작해야 하므로 여기서 직접 만든다.
 * <p>
 * {@code @Profile("e2e")}라 운영·로컬에서는 빈 자체가 만들어지지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Profile("e2e")
@Component
public class E2eDataInitializer implements ApplicationRunner {

    /** 비밀번호 정책(8자 이상, 대·소문자·특수문자)을 만족하는 공용 비밀번호. */
    public static final String PASSWORD = "E2e!pass1";

    public static final String ADMIN_EMAIL = "admin@e2e.test";
    public static final String USER_EMAIL = "user@e2e.test";
    public static final String OTHER_EMAIL = "other@e2e.test";
    public static final String GUEST_EMAIL = "guest@e2e.test";

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = saveUser("관리자", ADMIN_EMAIL, Role.ADMIN);
        User user = saveUser("테스터", USER_EMAIL, Role.USER);
        User other = saveUser("다른사람", OTHER_EMAIL, Role.USER);
        saveUser("미인증", GUEST_EMAIL, Role.GUEST);

        Post notice = savePost(admin, "공지 게시글", "관리자가 쓴 공지입니다.", Category.NOTICE);
        Post mine = savePost(user, "테스터의 글", "테스터가 쓴 자유 게시글입니다.", Category.FREE);
        savePost(other, "다른 사람의 글", "소유권 검사를 확인하는 글입니다.", Category.QNA);

        commentRepository.save(Comment.builder().content("첫 댓글입니다.").post(mine).user(user).build());
        commentRepository.save(Comment.builder().content("다른 사람의 댓글입니다.").post(notice).user(other).build());

        log.info("[E2E] 시드 데이터를 만들었습니다. users={}, posts={}",
                userRepository.count(), postRepository.count());
    }

    private User saveUser(String name, String email, Role role) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .build());
    }

    private Post savePost(User author, String title, String content, Category category) {
        return postRepository.save(Post.builder()
                .title(title)
                .content(content)
                .user(author)
                .category(category)
                .build());
    }
}
