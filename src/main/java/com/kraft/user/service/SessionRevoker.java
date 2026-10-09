package com.kraft.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 한 계정의 로그인 세션을 서버에서 끊는다. 비밀번호 변경 후 로그아웃을 화면 JS에 맡기면 API 직접 호출이나 후속 요청 실패 시
 * 세션이 살아 있고 다른 기기는 끊기지 않아, 세션을 탈취당해도 접근이 회수되지 않는다. 세션 저장소가 Spring Session JDBC라
 * {@link FindByIndexNameSessionRepository}로 principal 이름(불변 회원 번호의 문자열)별 세션을 찾는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SessionRevoker {

    /** 로그인 성공 시 세션에 심는 불변 회원 번호. 탈퇴 후 재가입한 다른 계정의 세션을 지연된 폐기가 잘못 지우지 않도록 {@code targetUserId}와 대조한다. */
    public static final String USER_ID_SESSION_ATTRIBUTE = "KRAFT_USER_ID";

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    /**
     * 해당 계정의 세션을 전부 폐기한다(요청을 보낸 현재 세션 포함 — 비밀번호를 바꾸면 모든 기기에서 다시 로그인한다).
     * {@code targetUserId}가 있으면 {@link #USER_ID_SESSION_ATTRIBUTE}가 다른 세션은 건너뛴다(재가입한 다른 계정 보호).
     * 속성이 없는 옛 세션은 구분할 수 없어 일치로 보고 지운다.
     *
     * @param targetUserId 대상 계정의 회원 번호. null이면 principal 이름의 세션을 구분 없이 전부 지운다.
     */
    public void revokeAll(String principalName, Long targetUserId) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principalName);
        int skipped = 0;
        int deleted = 0;
        for (Map.Entry<String, ? extends Session> entry : sessions.entrySet()) {
            Long sessionUserId = entry.getValue().getAttribute(USER_ID_SESSION_ATTRIBUTE);
            if (targetUserId != null && sessionUserId != null && !sessionUserId.equals(targetUserId)) {
                skipped++;
                continue;
            }
            sessionRepository.deleteById(entry.getKey());
            deleted++;
        }
        // 비밀번호 변경·재설정·탈퇴가 모두 이 경로를 쓴다. 사유는 부른 쪽이 로그로 남긴다.
        log.info("계정의 기존 세션 {}개를 폐기했습니다(다른 계정 소유로 건너뜀 {}건).", deleted, skipped);
    }
}
