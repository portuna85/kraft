package com.kraft.recommend;

import com.kraft.common.web.DeviceTokenSupport;
import com.kraft.common.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendation-sets")
public class RecommendationSetsController {

    private final RecommendationSetHistoryService recommendationSetHistoryService;
    private final DeviceTokenSupport deviceTokenSupport;

    public RecommendationSetsController(RecommendationSetHistoryService recommendationSetHistoryService,
                                         DeviceTokenSupport deviceTokenSupport) {
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.deviceTokenSupport = deviceTokenSupport;
    }

    @GetMapping
    public ResponseEntity<PageResponse<RecommendationSetSummary>> list(
            @RequestHeader(name = "X-Device-Token", required = true) String deviceToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String hash = deviceTokenSupport.requireHashedToken(deviceToken);
        Page<RecommendationSetSummary> result = recommendationSetHistoryService.list(hash, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecommendationSetSummary> get(
            @RequestHeader(name = "X-Device-Token", required = true) String deviceToken,
            @PathVariable long id) {
        String hash = deviceTokenSupport.requireHashedToken(deviceToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recommendationSetHistoryService.get(hash, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(name = "X-Device-Token", required = true) String deviceToken,
            @PathVariable long id) {
        String hash = deviceTokenSupport.requireHashedToken(deviceToken);
        recommendationSetHistoryService.delete(hash, id);
        return ResponseEntity.noContent().build();
    }
}
