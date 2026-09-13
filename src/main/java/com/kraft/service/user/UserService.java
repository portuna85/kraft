package com.kraft.service.user;

import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.service.support.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevoker sessionRevoker;

    @Transactional
    public Long signUp(String name, String email, String rawPassword) {
        if (userRepository.existsByName(name)) {
            throw new IllegalArgumentException("이미 사용중인 이름입니다. name=" + name);
        }
        if (userRepository.existsByEmailHash(EmailHasher.sha512Hex(email))) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다. email=" + email);
        }

        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.GUEST)
                .build();

        return userRepository.save(user).getId();
    }

    /**
     * 회원정보 변경은 비밀번호 변경만 가능하다는 기획 의도(User.java 클래스 주석)에 따라,
     * 반드시 현재 비밀번호를 확인한 뒤에만 새 비밀번호로 바꾼다.
     * <p>
     * 변경이 <b>커밋된 뒤</b> 이 계정의 모든 세션을 서버에서 폐기한다({@link SessionRevoker}).
     * 브라우저 JS의 후속 로그아웃에 맡기던 예전 방식은 API 직접 호출이나 후속 요청 실패에
     * 무력했고 다른 기기의 세션도 남겼다(개선 보고서 F04). 커밋 이후로 미루는 이유는,
     * 변경이 롤백되면 세션도 그대로 유지되어야 하기 때문이다.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        AfterCommit.run(() -> sessionRevoker.revokeAll(email));
    }

    @Transactional
    public void promoteToUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));
        user.promoteToUser();
    }
}
