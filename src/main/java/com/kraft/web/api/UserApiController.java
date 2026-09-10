package com.kraft.web.api;

import com.kraft.service.user.UserService;
import com.kraft.web.dto.user.SignUpRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
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
}
