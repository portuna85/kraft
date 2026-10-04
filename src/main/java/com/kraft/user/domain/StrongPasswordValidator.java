package com.kraft.user.domain;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** {@link StrongPassword}의 검사. 길이와 구성 규칙은 따로 보고하므로 둘 다 틀리면 메시지가 둘 나온다. */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final Pattern COMPOSITION = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).*$");

    private String label;

    @Override
    public void initialize(StrongPassword constraint) {
        this.label = constraint.label();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            return true;
        }

        boolean lengthOk = password.length() >= StrongPassword.MIN_LENGTH && password.length() <= StrongPassword.MAX_LENGTH;
        boolean compositionOk = COMPOSITION.matcher(password).matches();
        if (lengthOk && compositionOk) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        if (!lengthOk) {
            context.buildConstraintViolationWithTemplate(
                    label + "는 " + StrongPassword.MIN_LENGTH + "자 이상 " + StrongPassword.MAX_LENGTH + "자 이하여야 합니다.")
                    .addConstraintViolation();
        }
        if (!compositionOk) {
            context.buildConstraintViolationWithTemplate(
                    label + "는 대문자, 소문자, 특수문자를 각각 1자 이상 포함해야 합니다.")
                    .addConstraintViolation();
        }
        return false;
    }
}
