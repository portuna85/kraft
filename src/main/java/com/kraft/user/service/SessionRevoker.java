package com.kraft.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Set;

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

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    /**
     * 해당 계정의 세션을 <b>전부</b> 폐기한다(요청을 보낸 현재 세션 포함). 확정된 제품 정책에
     * 따라 비밀번호를 바꾸면 모든 기기에서 다시 로그인해야 한다.
     */
    public void revokeAll(String principalName) {
        Set<String> sessionIds = sessionRepository.findByPrincipalName(principalName).keySet();
        sessionIds.forEach(sessionRepository::deleteById);
        // 비밀번호 변경·재설정·탈퇴가 모두 이 경로를 쓴다. 사유는 부른 쪽이 로그로 남긴다.
        log.info("계정의 기존 세션 {}개를 폐기했습니다.", sessionIds.size());
    }
}
