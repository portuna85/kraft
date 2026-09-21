package com.kraft.user.domain;

import java.nio.charset.StandardCharsets;

/**
 * 비밀번호의 실제 저장 계약은 문자 수가 아니라 UTF-8 바이트 수다(B15).
 * <p>
 * DTO의 {@code @Size(max = 72)}는 문자 수만 본다. 그런데 실제 저장에 쓰이는
 * {@code PasswordEncoderFactories.createDelegatingPasswordEncoder()}(BCrypt)는 72
 * <b>바이트</b> 기준이라, 한글 72자(UTF-8로 216바이트)처럼 DTO 검증은 통과하지만 인코더가
 * {@code IllegalArgumentException("password cannot be more than 72 bytes")}를 던지는
 * 입력이 있었다 — 영문 원문 메시지가 그대로 클라이언트에 노출되고, "8자 이상 72자 이하"라는
 * 안내를 지킨 사용자가 이유를 알 수 없는 거절을 당했다({@code PasswordMultibyteBoundaryTest}
 * 참고).
 * <p>
 * 가입·비밀번호 변경·재설정 세 경로 모두 인코더를 부르기 전에 이 검사를 거쳐, 같은 한국어
 * 메시지로 미리 거절한다. encoder 교체는 기존 해시 호환성·rehash 정책이 별도로 필요한 더 큰
 * 작업이라 이번 범위에 포함하지 않는다 — 지금 encoder를 유지한 채 계약 차이만 없앤다.
 */
public final class PasswordBytePolicy {

    /** 현재 encoder(BCrypt)가 다루는 최대 바이트 수. */
    public static final int MAX_BYTES = 72;

    private PasswordBytePolicy() {
    }

    /**
     * @throws IllegalArgumentException UTF-8로 인코딩했을 때 {@value #MAX_BYTES}바이트를
     *                                   넘으면. null은 통과시킨다 — 필수 여부는 DTO의
     *                                   {@code @NotBlank}가 이미 담당한다.
     */
    public static void validate(String password) {
        if (password == null) {
            return;
        }
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "비밀번호는 UTF-8 기준 " + MAX_BYTES + "바이트를 넘을 수 없습니다. "
                            + "한글은 1자당 3바이트, 대부분의 이모지는 4바이트를 차지합니다.");
        }
    }
}
