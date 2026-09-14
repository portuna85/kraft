package com.kraft.domain.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * {@code User.email} 컬럼을 DB에 저장할 때는 AES로 암호화하고, 조회해 올 때는 복호화한다.
 * 조회/중복확인은 이 컬럼이 아니라 결정적 해시({@link EmailHasher})를 담은 별도의
 * {@code email_hash} 컬럼으로 하므로, 여기서는 매 호출마다 IV가 달라지는 비결정적 암호화
 * ({@code Encryptors.delux}, AES-256-GCM 기반)를 사용해 기밀성을 최대화한다. 암호화기 생성은
 * {@link EmailEncryption}에 모아 두었다 — 키 교체 도구가 같은 salt를 써야 하기 때문이다.
 * <p>
 * {@code @DataJpaTest}처럼 최소 컨텍스트만 로드하는 테스트 슬라이스에서는 이 컨버터를
 * {@code @Import(EmailAttributeConverter.class)}로 명시적으로 등록해야 Hibernate가
 * Spring이 관리하는(= {@code @Value}로 키가 주입된) 인스턴스를 사용한다.
 */
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
