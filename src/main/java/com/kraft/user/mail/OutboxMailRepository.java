package com.kraft.user.mail;

import com.kraft.user.domain.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxMailRepository extends JpaRepository<OutboxMail, Long> {

    List<OutboxMail> findByStatusOrderByIdAsc(OutboxMailStatus status, Limit limit);

    /** 재발송 요청 제한에 쓴다 — 이 회원에게 마지막으로 만든 메일이 언제인지 본다. */
    Optional<OutboxMail> findFirstByUserIdOrderByIdDesc(Long userId);

    /**
     * SENDING인 채로 오래 남은 것을 다시 집을 수 있게 한다. 발송 도중 프로세스가 죽으면
     * 그 메일은 영영 PENDING으로 돌아오지 못한다.
     */
    List<OutboxMail> findByStatusAndUpdatedAtBefore(OutboxMailStatus status, LocalDateTime threshold);

    /** 상태 보고에 쓴다 — 대기·실패가 쌓이면 메일이 안 나가고 있다는 뜻이다. */
    long countByStatus(OutboxMailStatus status);
}
