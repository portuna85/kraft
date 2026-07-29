package com.kraft.identity;

import com.kraft.common.error.ApiException;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.saved.SavedNumberClaimResult;
import com.kraft.saved.SavedNumberClientLockInitializer;
import com.kraft.saved.SavedNumberClientLockRepository;
import com.kraft.saved.SavedNumbersService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 계정 귀속 오케스트레이션. 동시성 잠금은 새로 만들지 않고 saved 패키지의
 * 클라이언트별 잠금 행({@link SavedNumberClientLockRepository}, B2 사건으로 채택된 레코드
 * 락 패턴)을 그대로 재사용한다. 기기 토큰 잠금 행 획득이 정확히 이
 * 목적이다.
 */
@Service
@Transactional
public class IdentityMergeService {

    private final DeviceClaimRepository deviceClaimRepository;
    private final SavedNumberClientLockRepository savedNumberClientLockRepository;
    private final SavedNumberClientLockInitializer savedNumberClientLockInitializer;
    private final SavedNumbersService savedNumbersService;
    private final RecommendationSetHistoryService recommendationSetHistoryService;
    private final Clock clock;
    private final Counter successCounter;
    private final Counter duplicateCounter;
    private final Counter conflictCounter;

    public IdentityMergeService(DeviceClaimRepository deviceClaimRepository,
                                 SavedNumberClientLockRepository savedNumberClientLockRepository,
                                 SavedNumberClientLockInitializer savedNumberClientLockInitializer,
                                 SavedNumbersService savedNumbersService,
                                 RecommendationSetHistoryService recommendationSetHistoryService,
                                 Clock clock,
                                 MeterRegistry meterRegistry) {
        this.deviceClaimRepository = deviceClaimRepository;
        this.savedNumberClientLockRepository = savedNumberClientLockRepository;
        this.savedNumberClientLockInitializer = savedNumberClientLockInitializer;
        this.savedNumbersService = savedNumbersService;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.clock = clock;
        this.successCounter = Counter.builder("kraft_identity_merge_total")
                .description("익명 기록 병합(기기 귀속) 성공 수")
                .tag("outcome", "success")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("kraft_identity_merge_total")
                .description("익명 기록 병합(기기 귀속) 성공 수")
                .tag("outcome", "duplicate")
                .register(meterRegistry);
        this.conflictCounter = Counter.builder("kraft_identity_merge_total")
                .description("익명 기록 병합(기기 귀속) 성공 수")
                .tag("outcome", "conflict")
                .register(meterRegistry);
    }

    /**
     * 같은 사용자가 같은 기기 토큰으로 재시도하면 멱등하게 성공한다 — 이미 옮겨진 행은
     * client_token_hash가 null이라 다시 조회되지 않으므로 자연히 0건 이동으로 끝난다.
     * 다른 사용자가 이미 귀속한 토큰이면 DEVICE_ALREADY_CLAIMED로 거부한다.
     */
    public IdentityMergeResult claim(String deviceTokenHash, Long userId) {
        savedNumberClientLockInitializer.ensureExists(deviceTokenHash);
        savedNumberClientLockRepository.lockByClientTokenHash(deviceTokenHash);

        Optional<DeviceClaim> existing = deviceClaimRepository.findByDeviceTokenHash(deviceTokenHash);
        if (existing.isPresent() && !existing.get().getClaimedByUserId().equals(userId)) {
            conflictCounter.increment();
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_ALREADY_CLAIMED",
                    "이 기기는 이미 다른 계정에 연결되어 있습니다.");
        }
        if (existing.isEmpty()) {
            deviceClaimRepository.save(new DeviceClaim(deviceTokenHash, userId, OffsetDateTime.now(clock)));
        } else {
            duplicateCounter.increment();
        }

        SavedNumberClaimResult savedResult = savedNumbersService.claimAll(deviceTokenHash, userId);
        int recommendationCount = recommendationSetHistoryService.claimAll(
                deviceTokenHash, userId, OffsetDateTime.now(clock));

        successCounter.increment();
        return new IdentityMergeResult(
                savedResult.mergedCount(), savedResult.duplicateCount(), recommendationCount);
    }
}
