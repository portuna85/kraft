package com.kraft.user.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 기동 직후, 설정된 이메일 HMAC pepper(와 암호화 키)가 DB에 저장된 값과 맞는지 일부 계정으로 확인한다. pepper가 다르면 이메일은 복호화돼도 계정 조회
 * (로그인·메일 대상 찾기)가 조용히 실패하고 가입은 중복으로 통과해 한참 뒤에야 알게 된다. 틀린 설정으로 서비스가 열려 있는 것보다 기동 실패(배포 스크립트의
 * readiness 확인이 롤백)가 낫다.
 * <p>
 * 가장 오래된 계정과 가장 최근 계정 몇 개를 본다: 저장된 {@code email_hmac}과 복호화한 이메일의 현재 pepper HMAC이 같아야 한다. 암호화 키가 틀리면
 * {@link EmailAttributeConverter}가 조회 단계에서 이미 실패한다. 전체 검증은 {@code EmailRekeyService#verifyAll}의 몫이다.
 * 켜는 속성은 {@code app.security.verify-email-pepper-on-startup}(운영 프로파일만 true)이고, 의도적으로 pepper를 교체하는 작업 중에는 false로 끈다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.security.verify-email-pepper-on-startup", havingValue = "true")
public class EmailPepperStartupVerifier implements ApplicationRunner {

    /** 오래된 쪽·최근 쪽에서 각각 확인할 계정 수. */
    static final int SAMPLE_SIZE = 10;

    private final UserRepository userRepository;

    public EmailPepperStartupVerifier(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!EmailHasher.hmacConfigured()) {
            return;
        }
        List<User> sample = new ArrayList<>();
        sample.addAll(userRepository.findAll(PageRequest.of(0, SAMPLE_SIZE, Sort.by("id").ascending())).getContent());
        sample.addAll(userRepository.findAll(PageRequest.of(0, SAMPLE_SIZE, Sort.by("id").descending())).getContent());

        verify(sample);
        log.info("이메일 HMAC pepper 기동 검증 통과 — 계정 {}건을 대조했습니다.", sample.stream().mapToLong(User::getId).distinct().count());
    }

    /** package-private: 표본 선택과 무관하게 대조 규칙만 확인한다. */
    static void verify(List<User> sample) {
        for (User user : sample) {
            if (!EmailHasher.hmacHex(user.getEmail()).equals(user.getEmailHmac())) {
                throw new IllegalStateException("EMAIL_HASH_PEPPER가 DB에 저장된 값과 맞지 않습니다(userId=" + user.getId()
                        + "). 백업 시점의 pepper(backup manifest의 email_pepper_fingerprint)를 쓰고 있는지 확인하세요. "
                        + "pepper를 의도적으로 바꾸는 중이라면 app.security.verify-email-pepper-on-startup=false로 끄고 전체 HMAC을 다시 계산하세요.");
            }
        }
    }
}
