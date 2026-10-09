package com.kraft.observability;

import com.kraft.user.mail.EmailSender;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link HealthReporter}가 기준을 넘긴 것을 발견하면 관리자에게 메일로 알린다.
 * <p>
 * 기존 {@code OutboxMail}(회원 1명 + 고정 템플릿 4종, DB 대기열)은 의도적으로 재사용하지
 * 않는다 — 장애 요약은 매번 다른 자유 형식 본문이고 특정 회원에 묶이지도 않아, 그 설계와
 * 맞지 않는다. 여기서는 {@link EmailSender}를 바로 불러 보낸다 — DB 왕복이 없으니 발송
 * 실패가 이 클래스 밖으로 전파되면 안 된다(관측이 서비스를 막으면 안 된다는 {@link
 * HealthReporter}와 같은 원칙).
 * <p>
 * 관리자 주소가 비어 있으면({@code app.metrics.alert-email} 기본값) 이 클래스는 아무것도
 * 하지 않는다 — 로컬 개발 환경에 별도 설정 없이도 조용히 꺼진 채로 있다.
 */
@Slf4j
public class AlertMailer {

    private final EmailSender emailSender;
    private final String alertEmail;
    private final Duration cooldown;

    /**
     * 종류(kind)별 마지막 발송 시각. 재시작하면 비워져 다음 첫 발생은 즉시 알린다 — 배포가
     * 잦은 이 서비스에서(main 푸시 = 배포) 재시작마다 쿨다운이 리셋되는 것은 "알림을 더 자주
     * 받는" 방향이라 안전한 쪽으로 어긋난다.
     */
    private final ConcurrentHashMap<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    public AlertMailer(EmailSender emailSender, String alertEmail, Duration cooldown) {
        this.emailSender = emailSender;
        this.alertEmail = alertEmail;
        this.cooldown = cooldown;
    }

    /** 관리자 주소를 설정하지 않은 환경(로컬 등)에서 쓰는 무동작 인스턴스. */
    public static AlertMailer disabled() {
        return new AlertMailer(null, "", Duration.ofHours(1));
    }

    /**
     * 기준을 넘긴 항목이 있고, 그중 하나라도 쿨다운이 지났으면 메일을 보낸다. 일부 항목만
     * 쿨다운이 지났어도 본문에는 현재 활성 중인 항목 전체를 담는다 — 받는 사람이 판단하는 데
     * 필요한 것은 "무엇이 새로 억제가 풀렸는지"가 아니라 "지금 무엇이 문제인지"다.
     */
    void alertIfDue(HealthSnapshot snapshot, HealthThresholds thresholds) {
        if (alertEmail == null || alertEmail.isBlank()) {
            return;
        }
        Set<String> kinds = snapshot.breachKinds(thresholds);
        if (kinds.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        boolean anyDue = kinds.stream().anyMatch(kind -> {
            Instant last = lastSentAt.get(kind);
            return last == null || Duration.between(last, now).compareTo(cooldown) >= 0;
        });
        if (!anyDue) {
            return;
        }
        kinds.forEach(kind -> lastSentAt.put(kind, now));

        String body = "다음 항목이 기준을 넘었습니다:\n- "
                + String.join("\n- ", snapshot.breaches(thresholds))
                + "\n\n이번 주기 전체 수치: " + snapshot.summary();
        try {
            emailSender.send(alertEmail, "[kraft] 상태 이상 감지", body);
        } catch (Exception e) {
            // 알림 발송 실패가 상태 점검 자체를 막으면 안 된다 — HealthReporter.report()와
            // 같은 원칙이다. 이미 ERROR 로그로 상태 이상 자체는 남아 있으니, 여기서는 발송
            // 실패만 별도로 남긴다.
            log.error("상태 이상 알림 메일 발송에 실패했습니다.", e);
        }
    }
}
