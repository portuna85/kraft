package com.kraft.observability;

import com.kraft.user.mail.EmailSender;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link HealthReporter}가 기준을 넘긴 것을 발견하면 관리자에게 메일로 알린다. 장애 요약은 매번 다른 자유 형식이고 특정 회원에
 * 묶이지 않아 {@code OutboxMail} 대기열을 쓰지 않고 {@link EmailSender}를 바로 부른다 — 발송 실패는 밖으로 전파하지 않는다
 * (관측이 서비스를 막으면 안 된다). 관리자 주소({@code app.metrics.alert-email})가 비어 있으면 아무것도 하지 않는다.
 */
@Slf4j
public class AlertMailer {

    private final EmailSender emailSender;
    private final String alertEmail;
    private final Duration cooldown;

    /** 종류(kind)별 마지막 발송 시각. 재시작하면 비워져 알림이 더 잦아지는 안전한 쪽으로 어긋난다. */
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

    /** 기준을 넘긴 항목 중 하나라도 쿨다운이 지났으면 보낸다. 본문에는 지금 활성인 항목 전체를 담는다("무엇이 새로 풀렸나"가 아니라 "지금 무엇이 문제인가"). */
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
            // 알림 실패가 상태 점검을 막으면 안 된다(상태 이상 자체는 ERROR 로그에 이미 남았다).
            log.error("상태 이상 알림 메일 발송에 실패했습니다.", e);
        }
    }
}
