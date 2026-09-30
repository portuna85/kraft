package com.kraft.support;

import com.kraft.user.domain.EmailHasher;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 스프링 컨텍스트 없이 도는 단위 테스트도 {@code EmailHasher.hmacHex}를 쓸 수 있게, 클래스 실행 전에
 * 테스트용 pepper를 정적 보관소에 심는다(application-test.yml의 값과 같다). 컨텍스트가 뜨는
 * 테스트는 같은 값을 다시 심으므로 서로 영향이 없다.
 */
public class EmailPepperExtension implements BeforeAllCallback {

    static final String TEST_PEPPER = "test-only-hash-pepper-0123456789";

    @Override
    public void beforeAll(ExtensionContext context) {
        EmailHasher.configurePepper(TEST_PEPPER);
    }
}
