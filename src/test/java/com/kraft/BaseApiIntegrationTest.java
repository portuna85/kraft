package com.kraft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.operationlog.WinningNumberOperationLogRepository;
import com.kraft.recommend.LottoRecommendationService;
import com.kraft.saved.SavedNumberRepository;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MockExternalFetchClientConfig.class)
abstract class BaseApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected WinningNumberRepository winningNumberRepository;

    @Autowired
    protected SavedNumberRepository savedNumberRepository;

    @Autowired
    protected WinningNumberOperationLogRepository winningNumberOperationLogRepository;

    @Autowired
    protected LottoRecommendationService lottoRecommendationService;

    @Autowired
    private CacheManager cacheManager;

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        winningNumberOperationLogRepository.deleteAll();
        savedNumberRepository.deleteAll();
        winningNumberRepository.deleteAll();
        // 회차 번호는 1부터 연속(1, 2)이어야 한다 — 추천 서비스의 이력 완전성 게이트(P1-05)가
        // 1회부터 최신 회차까지 빈틈없이 로드됐는지를 요구하므로, 중간에 구멍이 있는 회차 번호를
        // 쓰면 이 기본 픽스처를 상속하는 모든 통합 테스트가 추천 API에서 503을 받는다.
        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(
                2,
                LocalDate.of(2026, 6, 13),
                3, 11, 19, 28, 34, 42,
                7,
                2_000_000_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        ));
        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(
                1,
                LocalDate.of(2026, 6, 6),
                1, 9, 17, 23, 31, 45,
                8,
                1_800_000_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        ));
        // 리포지토리 직접 시딩은 WinningNumbersCollectedEvent를 발행하지 않으므로,
        // 추천 서비스의 이력 캐시를 수동으로 갱신해야 R2 fail-closed가 오탐하지 않는다.
        lottoRecommendationService.refreshHistoryCache();
    }

    protected long extractId(String response) throws Exception {
        return OBJECT_MAPPER.readTree(response).path("savedNumber").path("id").asLong();
    }
}
