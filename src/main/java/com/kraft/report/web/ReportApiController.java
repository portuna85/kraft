package com.kraft.report.web;

import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ReportApiController {

    private final ReportService reportService;

    /** 신고 접수. 로그인만 하면 할 수 있다 — 이메일 인증 전이라도 문제를 알릴 수는 있어야 한다. */
    @PostMapping("/api/v1/reports")
    public Long report(@Valid @RequestBody ReportSaveRequestDto requestDto, Authentication authentication) {
        return reportService.report(requestDto, authentication);
    }

    /**
     * 신고를 받아들여 대상을 지운다. 관리자 전용(SecurityConfig의 /api/v1/admin/**).
     *
     * @param suspendDays 0보다 크면 작성자를 그만큼 정지한다. 지우기만 해서는 반복하는 사람을
     *                    막지 못한다.
     */
    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable Long id,
                                         @RequestParam(defaultValue = "0") int suspendDays,
                                         Authentication authentication) {
        reportService.resolve(id, authentication, suspendDays);
        return ResponseEntity.noContent().build();
    }

    /** 문제가 없다고 판단해 신고만 닫는다. 관리자 전용. */
    @PostMapping("/api/v1/admin/reports/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, Authentication authentication) {
        reportService.reject(id, authentication);
        return ResponseEntity.noContent().build();
    }

    /**
     * B11: 두 관리자가 같은 신고를 동시에 resolve/reject하면(Report.version) 나중에 커밋하는
     * 쪽이 이 예외를 받는다. 이 실패는 트랜잭션 커밋 시점(서비스 메서드가 이미 반환한 뒤)에
     * 나므로 {@code ReportService} 안의 catch로는 잡을 수 없고, 호출한 쪽인 여기서 잡아야
     * 한다. 이 컨트롤러 안의 핸들러가 {@code ApiExceptionHandler}의 같은 예외 타입 핸들러
     * (게시글 편집 충돌 문구)보다 우선 적용된다.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentReportHandling(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "이미 처리된 신고입니다. 새로고침 후 다시 확인해 주세요.");
    }
}
