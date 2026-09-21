package com.kraft.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 한 계정의 로그인 세션을 서버에서 끊는다.
 * <p>
 * 예전에는 비밀번호를 바꿔도 서버가 하는 일은 해시 갱신뿐이었고, 로그아웃은 화면의 JS가 이어서
 * 호출하는 {@code /logout}에 맡겨져 있었다. 그래서 API만 직접 호출하거나 후속 로그아웃이
 * 실패하면 기존 세션이 그대로 살아 있었고, 다른 기기의 세션은 애초에 끊기지 않았다 —
 * 세션을 탈취당한 상태에서 비밀번호를 바꿔도 접근 회수가 되지 않는다는 뜻이다(개선 보고서 F04).
 * <p>
 * 세션 저장소가 Spring Session JDBC({@code spring.session.store-type: jdbc})이므로
 * {@code JdbcIndexedSessionRepository}가 {@link FindByIndexNameSessionRepository}를 구현한다.
 * principal 이름은 로그인 식별자인 이메일이다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SessionRevoker {

    /**
     * 로그인 성공 시 세션에 심어 두는 불변 회원 번호(B02). principal 이름(이메일)은 탈퇴 후
     * 같은 주소로 재가입하면 재사용될 수 있어, 지연된 폐기 요청이 "그 이메일의 세션 전부"를
     * 지우면 재가입한 새 계정의 살아있는 세션까지 함께 지울 수 있다. {@code targetUserId}가
     * 주어지면 이 속성으로 대상 계정을 다시 한번 확인한다.
     */
    public static final String USER_ID_SESSION_ATTRIBUTE = "KRAFT_USER_ID";

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    /**
     * 해당 계정의 세션을 <b>전부</b> 폐기한다(요청을 보낸 현재 세션 포함). 확정된 제품 정책에
     * 따라 비밀번호를 바꾸면 모든 기기에서 다시 로그인해야 한다.
     * <p>
     * {@code targetUserId}가 주어지면(B02), principal 이름으로 찾은 세션 중 {@link #USER_ID_SESSION_ATTRIBUTE}
     * 속성이 이 값과 다른 세션은 건너뛴다 — 탈퇴 후 같은 이메일로 재가입한 <b>다른</b> 계정의
     * 세션을 잘못 지우지 않기 위해서다. 그 속성이 아직 없는 세션(이 변경 이전에 만들어진 세션)은
     * 구분할 방법이 없으므로 일치로 간주하고 지운다 — 세션 수명이 유한하므로 위험 구간은 자연히
     * 줄어든다.
     *
     * @param targetUserId 지울 대상 계정의 불변 회원 번호. null이면 구분 없이 principal 이름의
     *                      세션을 전부 지운다(과거 호출 호환용 — 새 코드는 항상 값을 넘긴다).
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
