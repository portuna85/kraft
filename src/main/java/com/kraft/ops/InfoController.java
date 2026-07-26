package com.kraft.ops;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InfoController {

    private final Clock clock;

    public InfoController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/status")
    public InfoStatusResponse status() {
        return new InfoStatusResponse(
                "kraft-lotto",
                "정상",
                ZoneId.of("Asia/Seoul").getId(),
                Instant.now(clock).atZone(ZoneId.of("Asia/Seoul")).toString());
    }
}
