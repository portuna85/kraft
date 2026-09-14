package com.kraft.user.mail;

import com.kraft.user.domain.User;
/**
 * 보낼 메일 한 통의 상태.
 * <p>
 * 값 목록은 Hibernate가 MariaDB에 만드는 네이티브 ENUM과 같도록 알파벳 순으로 쓴다
 * (V1__baseline.sql의 users.role 주석 참고).
 */
public enum OutboxMailStatus {

    /** 시도 횟수를 모두 쓰고도 실패했다. 더 보내지 않는다. */
    FAILED,

    /** 아직 보내지 않았다. 다음 발송 차례에 집는다. */
    PENDING,

    /** 지금 보내는 중이다. 두 작업자가 같은 메일을 집지 않게 하는 표시다. */
    SENDING,

    SENT
}
