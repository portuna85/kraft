package com.kraft.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code rekey} 프로파일로 앱을 띄우면 이메일 키 교체를 한 번 수행하고 종료한다.
 *
 * <pre>
 * java -jar kraft.jar --spring.profiles.active=rekey
 * </pre>
 *
 * 전용 프로파일로 가둔 이유는 <b>실수로 돌 일이 없게</b> 하기 위해서다. {@code local}·{@code prod}
 * 어디에도 이 빈이 활성화될 경로가 없다. 자세한 절차는 README "백업·복구"에 있다 —
 * <b>DB 백업이 선행 조건</b>이다.
 * <p>
 * 끝나면 종료 코드를 남기고 내려간다(성공 0, 실패 1). 서비스로 남지 않으므로 배치처럼 쓸 수 있다.
 */
@Slf4j
@RequiredArgsConstructor
@Profile("rekey")
@Component
public class EmailRekeyRunner implements ApplicationRunner {

    private final EmailRekeyService emailRekeyService;
    private final ConfigurableApplicationContext context;

    /** 앞으로 쓸 키. 평소 앱이 쓰는 것과 같은 설정이라, 교체 후 그대로 기동하면 된다. */
    @Value("${app.security.email-encryption-key}")
    private String newKey;

    /** 지금 DB에 저장된 암호문을 만든 키. 이 실행에만 필요하다. */
    @Value("${app.security.old-email-encryption-key:}")
    private String oldKey;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = rekey();
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private int rekey() {
        try {
            EmailRekeyService.Result result = emailRekeyService.rekeyAll(oldKey, newKey);
            log.info("이제 EMAIL_ENCRYPTION_KEY를 새 키로 바꾼 뒤 평소대로 기동하세요. 대상 {}건.",
                    result.total());
            return 0;
        } catch (Exception e) {
            // 주소는 남기지 않는다. 어디서 멈췄는지는 예외 메시지의 userId로 충분하다.
            log.error("이메일 키 교체에 실패했습니다. DB는 백업 시점으로 되돌리고 원인을 확인하세요.", e);
            return 1;
        }
    }
}
