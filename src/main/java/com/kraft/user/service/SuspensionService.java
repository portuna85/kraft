package com.kraft.user.service;

import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.dto.SuspendedUserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 관리자가 정지를 들여다보고 푸는 쪽.
 * <p>
 * 정지를 <b>거는</b> 것은 신고 처리의 일부라 {@code ReportService}에 있고, 여기는 그 결과를
 * 목록으로 보고 필요하면 되돌리는 경로만 맡는다.
 * <p>
 * 해제 기능이 필요한 이유는 정지가 사람의 판단이기 때문이다. 기간이 지나면 저절로 풀리지만,
 * 잘못 건 정지를 일주일 동안 그대로 두는 것은 그 사람에게 실제 손해다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class SuspensionService {

    private final UserRepository userRepository;

    /** 지금 정지 중인 회원. 만료된 정지는 목록에 나오지 않는다(이미 풀린 것과 같다). */
    public Page<SuspendedUserDto> findSuspended(Pageable pageable) {
        return userRepository
                .findBySuspendedUntilAfterOrderBySuspendedUntilAsc(LocalDateTime.now(), pageable)
                .map(user -> new SuspendedUserDto(
                        user.getId(), user.getName(), user.getSuspendedUntil(), user.getSuspensionReason()));
    }

    /**
     * 기간을 다 채우기 전에 푼다. 사유는 지우지 않는다 — 무슨 일이 있었는지는 남아야 한다.
     */
    @Transactional
    public void lift(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

        if (!user.isSuspended()) {
            throw new IllegalArgumentException("정지 중인 계정이 아닙니다. id=" + userId);
        }

        user.liftSuspension();
        log.info("정지를 해제했습니다. userId={}", userId);
    }
}
