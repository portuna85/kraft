package com.kraft.user.domain;

import com.kraft.shared.exception.BusinessValidationException;
import java.nio.charset.StandardCharsets;

/**
 * 비밀번호의 저장 계약은 문자 수가 아니라 UTF-8 바이트 수다. DTO의 {@code @Size(max = 72)}는 문자 수만 보지만 BCrypt는 72
 * <b>바이트</b> 기준이라, 한글 72자(216바이트)는 DTO를 통과하고 인코더가 영문 {@code IllegalArgumentException}을 던져 사용자가
 * 이유를 알 수 없는 거절을 당한다({@code PasswordMultibyteBoundaryTest}). 가입·변경·재설정 모두 인코더 전에 이 검사로 같은
 * 한국어 메시지로 거절한다. 인코더 교체는 해시 호환성·rehash 정책이 필요한 더 큰 작업이라 하지 않는다.
 */
public final class PasswordBytePolicy {

    /** 현재 encoder(BCrypt)가 다루는 최대 바이트 수. */
    public static final int MAX_BYTES = 72;

    private PasswordBytePolicy() {
    }

    /** @throws IllegalArgumentException UTF-8로 {@value #MAX_BYTES}바이트를 넘으면. null은 통과시킨다(필수 여부는 DTO의 {@code @NotBlank} 몫). */
    public static void validate(String password) {
        if (password == null) {
            return;
        }
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > MAX_BYTES) {
            throw new BusinessValidationException(
                    "비밀번호는 UTF-8 기준 " + MAX_BYTES + "바이트를 넘을 수 없습니다. "
                            + "한글은 1자당 3바이트, 대부분의 이모지는 4바이트를 차지합니다.");
        }
    }
}
