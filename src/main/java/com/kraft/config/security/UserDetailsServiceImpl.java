package com.kraft.config.security;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
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
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        // username은 이메일 그대로 둔다(서비스 계층이 authentication.getName()으로 회원을 찾는다).
        // 화면 표시용 닉네임은 displayName으로 따로 싣는다.
        return new KraftUserDetails(
                user.getEmail(),
                user.getPassword(),
                user.getName(),
                List.of(new SimpleGrantedAuthority(user.getRoleKey()))
        );
    }
}
