package com.kraft.web.api;

import com.kraft.service.user.UserService;
import com.kraft.web.dto.user.ChangePasswordRequestDto;
import com.kraft.web.dto.user.SignUpRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserApiController {

    private final UserService userService;

    @PostMapping("/api/v1/users")
    public Long signUp(@Valid @RequestBody SignUpRequestDto requestDto) {
        return userService.signUp(requestDto.name(), requestDto.email(), requestDto.password());
    }

    @PutMapping("/api/v1/users/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDto requestDto,
                                                Authentication authentication) {
        userService.changePassword(authentication.getName(), requestDto.currentPassword(), requestDto.newPassword());
        return ResponseEntity.noContent().build();
    }
}
