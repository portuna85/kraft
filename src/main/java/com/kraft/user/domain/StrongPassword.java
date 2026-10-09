package com.kraft.user.domain;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 형식 규칙(8~72자, 대문자·소문자·특수문자 각 1자 이상)을 한 곳에 둔 제약이다(가입·변경·재설정 DTO 공용, 프런트
 * {@code core/constants.js}의 길이 상수와 맞출 기준). 문자 수 기준이며, 저장 계약인 UTF-8 바이트 수는 {@link PasswordBytePolicy}가 본다.
 * null·빈 값은 통과시킨다({@code @NotBlank}가 따로 본다). 메시지의 주어({@link #label()})만 화면마다 다르다.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    int MIN_LENGTH = 8;
    int MAX_LENGTH = 72;

    /** 메시지 주어. */
    String label() default "비밀번호";

    String message() default "비밀번호 형식이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
