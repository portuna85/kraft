package com.kraft.logistics.domain.user;

import com.kraft.logistics.domain.user.dto.UserSignupRequestDto;
import com.kraft.logistics.domain.user.dto.UserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponseDto signup(UserSignupRequestDto dto) {
        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
                .role(Role.USER)
                .build();

        User saved = userRepository.save(user);
        return new UserResponseDto(saved.getId(), saved.getUsername(), saved.getNickname(), saved.getRole());
    }
}
