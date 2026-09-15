package com.kraft.user.mail;

import com.kraft.user.domain.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxMailRepository extends JpaRepository<OutboxMail, Long> {

    List<OutboxMail> findByStatusOrderByIdAsc(OutboxMailStatus status, Limit limit);

    /**
     * 요청 제한에 쓴다 — 이 회원에게 이 종류의 메일을 마지막으로 만든 것이 언제인지 본다.
     * <p>
     * 종류를 함께 보는 것이 중요하다. 종류를 가리지 않으면 가입 직후 인증 메일을 받은 사람이
     * 곧바로 비밀번호 재설정을 요청했을 때 그 요청이 조용히 무시된다.
     */
    Optional<OutboxMail> findFirstByUserIdAndKindOrderByIdDesc(Long userId, OutboxMailKind kind);

    /**
     * SENDING인 채로 오래 남은 것을 다시 집을 수 있게 한다. 발송 도중 프로세스가 죽으면
     * 그 메일은 영영 PENDING으로 돌아오지 못한다.
     */
    List<OutboxMail> findByStatusAndUpdatedAtBefore(OutboxMailStatus status, LocalDateTime threshold);

    /** 상태 보고에 쓴다 — 대기·실패가 쌓이면 메일이 안 나가고 있다는 뜻이다. */
    long countByStatus(OutboxMailStatus status);
}
