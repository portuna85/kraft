package com.kraft.config.security;

import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 회원입니다. email=" + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRoleKey())
                .build();
    }
}
