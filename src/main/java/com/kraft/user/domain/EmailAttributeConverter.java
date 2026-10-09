package com.kraft.user.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * {@code User.email}을 저장할 때 암호화하고 조회할 때 복호화한다. 조회·중복확인은 별도 {@code email_hmac} 컬럼으로 하므로 여기서는
 * 호출마다 IV가 달라지는 비결정적 암호화({@code Encryptors.delux})로 기밀성을 최대화한다. 암호화기 생성은 키 교체 도구가 같은 salt를
 * 써야 해서 {@link EmailEncryption}에 모았다. {@code @DataJpaTest} 같은 최소 슬라이스에서는
 * {@code @Import(EmailAttributeConverter.class)}로 등록해야 Hibernate가 키가 주입된 인스턴스를 쓴다.
 */
// JPA 슬라이스 테스트가 User를 저장할 때 email_hmac 계산에 HMAC pepper 설정이 필요하다.
@Import(EmailHmacConfiguration.class)
@Component
@Converter
public class EmailAttributeConverter implements AttributeConverter<String, String> {

    private final TextEncryptor encryptor;

    public EmailAttributeConverter(@Value("${app.security.email-encryption-key}") String encryptionKey) {
        this.encryptor = EmailEncryption.encryptor(encryptionKey);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : encryptor.decrypt(dbData);
    }
}
