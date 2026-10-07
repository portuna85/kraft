package com.kraft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class KraftApplication {

    /**
     * 시간은 KST로 고정한다. 엔티티 시각이 {@code LocalDateTime}이고 곳곳이 {@code LocalDateTime.now()}를
     * 쓰므로, 이 앱의 "지금"은 JVM 기본 시간대가 정한다 — 도커 {@code TZ} 같은 실행 환경 설정에 맡기면
     * 환경이 달라질 때 DB에 저장되는 시각이 말없이 어긋난다. 컨텍스트가 뜨기 전에 못 박는다.
     */
    public static final String ZONE_ID = "Asia/Seoul";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_ID));
        SpringApplication.run(KraftApplication.class, args);
    }

}
