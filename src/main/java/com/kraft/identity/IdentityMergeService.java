package com.kraft.identity;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.community.user.CommunityUserRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 계정 귀속 오케스트레이션. 동시성 잠금은 새로 만들지 않고 saved 패키지의
 * 클라이언트별 잠금 행({@link SavedNumberClientLockRepository}, B2 사건으로 채택된 레코드
 * 락 패턴)을 그대로 재사용한다. 기기 토큰 잠금 행 획득이 정확히 이
 * 목적이다.
 *
 * <p>M-02: 기기 토큰 잠금에 더해 계정(owner) 행도 {@code communityUserRepository.lockById}로
 * 잠근다 — {@code SavedNumbersService.saveForOwner}가 같은 계정 행을 잠그는 동안 이
 * 메서드가 락 없이 claimAll을 실행하면, "로그인 직후 저장"과 "다른 기기에서의 claim"이
 * 동시에 같은 계정에 병합·저장을 시도해 한도 검사를 서로 우회할 수 있었다. 락 순서는
 * 항상 기기 토큰 → 계정 순으로 고정한다(이 순서를 다른 경로가 뒤집으면 교착 가능성이
 * 생긴다 — 현재 saveForOwner는 계정 행만 잠그므로 아직 실제 교착 경로는 없지만, 향후
 * 기기 토큰 잠금을 추가로 거는 다른 경로가 생기면 반드시 이 순서를 따라야 한다).
 */
@Service
@Transactional
public class IdentityMergeService {

    private final DeviceClaimRepository deviceClaimRepository;
    private final SavedNumberClientLockRepository savedNumberClientLockRepository;
    private final SavedNumberClientLockInitializer savedNumberClientLockInitializer;
    private final CommunityUserRepository communityUserRepository;
    private final SavedNumbersService savedNumbersService;
    private final RecommendationSetHistoryService recommendationSetHistoryService;
    private final Clock clock;
    private final Counter successCounter;
    private final Counter duplicateCounter;
    private final Counter conflictCounter;

    public IdentityMergeService(DeviceClaimRepository deviceClaimRepository,
                                 SavedNumberClientLockRepository savedNumberClientLockRepository,
                                 SavedNumberClientLockInitializer savedNumberClientLockInitializer,
                                 CommunityUserRepository communityUserRepository,
                                 SavedNumbersService savedNumbersService,
                                 RecommendationSetHistoryService recommendationSetHistoryService,
                                 Clock clock,
                                 MeterRegistry meterRegistry) {
        this.deviceClaimRepository = deviceClaimRepository;
        this.savedNumberClientLockRepository = savedNumberClientLockRepository;
        this.savedNumberClientLockInitializer = savedNumberClientLockInitializer;
        this.communityUserRepository = communityUserRepository;
        this.savedNumbersService = savedNumbersService;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.clock = clock;
        // KB-09: 세 카운터의 description이 전부 동일한 복붙이었다 — outcome 태그로만
        // sum by(outcome) 집계가 구분되고, 사람이 읽는 description은 outcome마다 실제로
        // 다른 의미를 설명해야 한다.
        this.successCounter = Counter.builder("kraft_identity_merge_total")
                .description("익명 기록을 신규로 계정에 병합 성공한 수")
                .tag("outcome", "success")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("kraft_identity_merge_total")
                .description("이미 이 계정에 귀속된 기기 토큰으로 멱등 재시도된 수")
                .tag("outcome", "duplicate")
                .register(meterRegistry);
        this.conflictCounter = Counter.builder("kraft_identity_merge_total")
                .description("다른 계정에 이미 귀속된 기기 토큰이라 거부된 수")
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
        // M-02: 기기 토큰 다음으로 계정 행을 잠근다(순서 고정, 클래스 Javadoc 참고) —
        // saveForOwner와 같은 락을 공유해 "로그인 직후 저장"과 "claim"이 동시에 같은
        // 계정의 한도 검사를 우회하지 못하게 한다.
        communityUserRepository.lockById(userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.COMMUNITY_USER_NOT_FOUND, "계정을 찾을 수 없습니다."));

        Optional<DeviceClaim> existing = deviceClaimRepository.findByDeviceTokenHash(deviceTokenHash);
        if (existing.isPresent() && !existing.get().getClaimedByUserId().equals(userId)) {
            conflictCounter.increment();
            throw new ApiException(ApiErrorCode.DEVICE_ALREADY_CLAIMED,
                    "이 기기는 이미 다른 계정에 연결되어 있습니다.");
        }
        // KB-09: 예전에는 duplicate 분기에서도 맨 아래 successCounter.increment()가 항상
        // 실행돼, sum by(outcome)이 멱등 재시도 1건을 success+duplicate 두 건으로
        // 이중 집계했다 — outcome별로 정확히 하나의 카운터만 늘어나야 한다.
        if (existing.isEmpty()) {
            deviceClaimRepository.save(new DeviceClaim(deviceTokenHash, userId, OffsetDateTime.now(clock)));
            successCounter.increment();
        } else {
            duplicateCounter.increment();
        }

        SavedNumberClaimResult savedResult = savedNumbersService.claimAll(deviceTokenHash, userId);
        int recommendationCount = recommendationSetHistoryService.claimAll(
                deviceTokenHash, userId, OffsetDateTime.now(clock));

        return new IdentityMergeResult(
                savedResult.mergedCount(), savedResult.duplicateCount(), savedResult.skippedForLimitCount(),
                recommendationCount);
    }
}
