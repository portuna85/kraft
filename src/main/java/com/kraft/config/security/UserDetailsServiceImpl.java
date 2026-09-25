package com.kraft.config.security;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.EmailPolicy;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String rawEmail) throws UsernameNotFoundException {
        String email = EmailPolicy.normalize(rawEmail);
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        // 탈퇴한 계정은 없는 계정처럼 다룬다. 탈퇴 시 비밀번호를 아무도 맞힐 수 없는 값으로
        // 덮어쓰므로 이 검사가 없어도 로그인은 안 되지만, 경계를 비밀번호에만 기대지 않는다.
        if (user.isWithdrawn()) {
            throw new UsernameNotFoundException("탈퇴한 회원입니다. email=" + EmailMasker.mask(email));
        }

        // username(=Authentication.getName())은 이제 회원 id다(BE-04). 이메일은 principal에
        // 싣지 않는다 — 세션 BLOB에 평문으로 직렬화되기 때문이다(F01). 필요한 곳은 CurrentUser로
        // DB에서 읽는다. 화면 표시용 닉네임은 displayName으로 싣는다.
        return new KraftUserDetails(
                user.getId(),
                user.getPassword(),
                user.getName(),
                List.of(new SimpleGrantedAuthority(user.getRoleKey()))
        );
    }
}
