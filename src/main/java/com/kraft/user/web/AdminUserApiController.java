package com.kraft.user.web;

import com.kraft.user.service.SuspensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class AdminUserApiController {

    private final SuspensionService suspensionService;

    /**
     * 정지를 기간 전에 푼다. 관리자 전용(SecurityConfig의 /api/v1/admin/**).
     * <p>
     * 정지는 사람의 판단이라 되돌릴 길이 필요하다. 기간이 지나면 저절로 풀리지만, 잘못 건
     * 정지를 일주일 동안 그대로 두는 것은 그 사람에게 실제 손해다.
     */
    @PostMapping("/api/v1/admin/users/{id}/suspension/lift")
    public ResponseEntity<Void> liftSuspension(@PathVariable Long id) {
        suspensionService.lift(id);
        return ResponseEntity.noContent().build();
    }
}
