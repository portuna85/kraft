package com.kraft.saved;

import com.kraft.common.web.DeviceTokenSupport;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/saved")
public class SavedNumbersController {

    private final SavedNumbersService savedNumbersService;
    private final DeviceTokenSupport deviceTokenSupport;

    public SavedNumbersController(SavedNumbersService savedNumbersService, DeviceTokenSupport deviceTokenSupport) {
        this.savedNumbersService = savedNumbersService;
        this.deviceTokenSupport = deviceTokenSupport;
    }

    @GetMapping
    public ResponseEntity<List<SavedNumberResponse>> list(
            @RequestHeader(name = "X-Device-Token", required = true) String deviceToken) {
        List<SavedNumberResponse> result = savedNumbersService.list(deviceTokenSupport.requireHashedToken(deviceToken));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping("/matches")
    public ResponseEntity<List<SavedNumberMatchResult>> matches(
            @RequestHeader(name = "X-Device-Token", required = true) String deviceToken,
            @RequestParam(name = "round", defaultValue = "latest") String round) {
        String hash = deviceTokenSupport.requireHashedToken(deviceToken);
        List<SavedNumberMatchResult> result = savedNumbersService.compareWithRound(hash, round);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping
    // 이미 저장된 조합이면 200, 새로 저장됐으면 201 — result.created()에 따라 갈린다(중복 저장 안내 vs 신규 생성).
    @ApiResponses({@ApiResponse(responseCode = "200"), @ApiResponse(responseCode = "201")})
    public ResponseEntity<SaveNumberResult> save(@RequestHeader(name = "X-Device-Token", required = true) String deviceToken,
                                                 @Valid @RequestBody CreateSavedNumberRequest request) {
        SaveNumberResult result = savedNumbersService.save(deviceTokenSupport.requireHashedToken(deviceToken), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> delete(@RequestHeader(name = "X-Device-Token", required = true) String deviceToken,
                                       @PathVariable long id) {
        savedNumbersService.delete(deviceTokenSupport.requireHashedToken(deviceToken), id);
        return ResponseEntity.noContent().build();
    }
}
