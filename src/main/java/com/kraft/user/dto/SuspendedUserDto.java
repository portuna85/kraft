package com.kraft.user.dto;

import java.time.LocalDateTime;

/**
 * 관리자 화면의 "정지 중인 회원" 한 줄.
 * <p>
 * 이메일은 담지 않는다. 정지를 풀지 말지 판단하는 데 필요한 것은 닉네임·기간·사유이고,
 * 주소는 암호화해 저장하는 값이라 화면으로 꺼낼 이유가 없다.
 */
public record SuspendedUserDto(
        Long id,
        String name,
        LocalDateTime suspendedUntil,
        String suspensionReason
) {
}
