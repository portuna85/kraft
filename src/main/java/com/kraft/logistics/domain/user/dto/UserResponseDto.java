package com.kraft.logistics.domain.user.dto;

import com.kraft.logistics.domain.user.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponseDto {
    private Long id;
    private String username;
    private String nickname;
    private Role role;
}
