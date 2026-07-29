package com.kraft.recommend;

import com.kraft.common.lotto.LottoNumbers;
import com.kraft.common.web.DeviceTokenSupport;
import com.kraft.winningnumber.FirstPrizeHistoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/numbers")
public class RecommendApiController {

    private final LottoRecommendationService lottoRecommendationService;
    private final DeviceTokenSupport deviceTokenSupport;
    private final FirstPrizeHistoryService firstPrizeHistoryService;

    public RecommendApiController(LottoRecommendationService lottoRecommendationService,
                                  DeviceTokenSupport deviceTokenSupport,
                                  FirstPrizeHistoryService firstPrizeHistoryService) {
        this.lottoRecommendationService = lottoRecommendationService;
        this.deviceTokenSupport = deviceTokenSupport;
        this.firstPrizeHistoryService = firstPrizeHistoryService;
    }

    @PostMapping("/recommend")
    public RecommendNumbersResponse recommend(
            @RequestHeader(name = "X-Device-Token", required = false) String deviceToken,
            @Valid @RequestBody(required = false) RecommendNumbersRequest request) {
        // X-Device-Token은 선택이다 — 보내면 추천 세트가 영속화되어 이력 조회가 가능해지고,
        // 보내지 않으면(기존 호환 클라이언트) 기존과 동일하게 stateless로 동작한다.
        String clientTokenHash = deviceToken == null || deviceToken.isBlank()
                ? null
                : deviceTokenSupport.requireHashedToken(deviceToken);
        return lottoRecommendationService.recommend(request, clientTokenHash);
    }

    @GetMapping("/check")
    public CombinationCheckResponse check(@RequestParam @LottoNumbers List<Integer> numbers) {
        return new CombinationCheckResponse(firstPrizeHistoryService.findByNumbers(numbers));
    }
}
