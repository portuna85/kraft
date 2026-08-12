package com.kraft;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        // 이 서비스는 한국어 전용이고 모든 사용자 대면 메시지가 하드코딩된 한국어 문자열이다.
        // Bean Validation(Hibernate Validator)의 내장 제약조건 메시지(@Min 등)만은 예외로,
        // JVM 기본 로케일에 따라 인터폴레이션된다 — 컨테이너 base image는 LANG을 설정하지
        // 않아 JVM 기본 로케일이 환경마다(로컬 Windows vs CI Ubuntu vs 배포 컨테이너)
        // 달라지고, 그 결과 같은 제약조건 위반이 환경에 따라 한국어("0 이상이어야 합니다")
        // 또는 영어("must be greater than or equal to 0") 메시지로 새어나갈 수 있었다.
        // 기본 로케일을 명시적으로 고정해 이 앱의 나머지 부분과 일관되게 만든다.
        Locale.setDefault(Locale.of("ko", "KR"));
        SpringApplication.run(Application.class, args);
    }
}
