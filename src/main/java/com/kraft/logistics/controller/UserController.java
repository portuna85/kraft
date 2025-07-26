package com.kraft.logistics.controller;

import com.kraft.logistics.domain.user.UserService;
import com.kraft.logistics.domain.user.dto.UserSignupRequestDto;
import com.kraft.logistics.domain.user.dto.UserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public UserResponseDto signup(@RequestBody UserSignupRequestDto dto) {
        return userService.signup(dto);
    }
}
