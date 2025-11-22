package com.kraft.service;

import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.user.SignupRequestDto;
import com.kraft.web.dto.user.UserProfileResponseDto;
import com.kraft.common.exception.DuplicateResourceException;
import com.kraft.common.exception.ResourceNotFoundException;
import com.kraft.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long register(SignupRequestDto requestDto) {
        validateDuplicateName(requestDto.getName());
        validateDuplicateEmail(requestDto.getEmail());

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        User user = requestDto.toEntity(encodedPassword);

        User savedUser = userRepository.save(user);
        log.info("회원가입 성공: userId={}, name={}", savedUser.getId(), savedUser.getName());

        return savedUser.getId();
    }

    @Transactional
    public Long register(String name, String rawPassword, String email) {
        validateDuplicateName(name);
        validateDuplicateEmail(email);

        User user = User.of(name, passwordEncoder.encode(rawPassword), email);
        User savedUser = userRepository.save(user);

        log.info("회원가입 성공: userId={}, name={}", savedUser.getId(), savedUser.getName());
        return savedUser.getId();
    }

    @Transactional(readOnly = true)
    public User findByName(String name) {
        return userRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", name));
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDto getProfile(Long userId) {
        User user = findById(userId);
        return UserProfileResponseDto.from(user);
    }

    @Transactional
    public UserProfileResponseDto updateEmail(Long userId, String newEmail) {
        User user = findById(userId);

        if (userRepository.existsByEmailAndIdNot(newEmail, userId)) {
            throw new DuplicateResourceException("이메일", "email", newEmail);
        }

        user.updateEmail(newEmail);
        log.info("이메일 변경 성공: userId={}, newEmail={}", userId, newEmail);

        return UserProfileResponseDto.from(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findById(userId);

        if (!user.isSamePassword(currentPassword, passwordEncoder)) {
            throw new UnauthorizedException("현재 비밀번호가 일치하지 않습니다");
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        log.info("비밀번호 변경 성공: userId={}", userId);
    }

    @Transactional
    public void delete(Long userId) {
        User user = findById(userId);
        userRepository.delete(user);
        log.info("회원 탈퇴 성공: userId={}", userId);
    }

    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
    }

    private void validateDuplicateName(String name) {
        if (userRepository.existsByName(name)) {
            throw new DuplicateResourceException("사용자 ID", "name", name);
        }
    }

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("이메일", "email", email);
        }
    }
}
