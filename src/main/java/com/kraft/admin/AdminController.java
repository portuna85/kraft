package com.kraft.admin;

import com.kraft.common.web.ClientIpResolver;
import com.kraft.common.web.PageResponse;
import com.kraft.winningnumber.WinningNumberBackfillService;
import com.kraft.winningnumber.WinningNumberCollectionService;
import com.kraft.winningnumber.WinningNumberQueryService;
import com.kraft.winningnumber.WinningNumberResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final int ROUNDS_MAX_PAGE_SIZE = 100;
    private static final int AUDIT_MAX_PAGE_SIZE = 200;

    private final WinningNumberQueryService queryService;
    private final WinningNumberCollectionService collectionService;
    private final WinningNumberBackfillService backfillService;
    private final AdminAuditLogService auditLogService;
    private final ClientIpResolver ipResolver;

    public AdminController(WinningNumberQueryService queryService,
                           WinningNumberCollectionService collectionService,
                           WinningNumberBackfillService backfillService,
                           AdminAuditLogService auditLogService,
                           ClientIpResolver ipResolver) {
        this.queryService = queryService;
        this.collectionService = collectionService;
        this.backfillService = backfillService;
        this.auditLogService = auditLogService;
        this.ipResolver = ipResolver;
    }

    @GetMapping({"", "/"})
    public String root() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        queryService.findLatest().ifPresent(w -> model.addAttribute("latest", AdminRoundView.from(w)));
        return "admin/dashboard";
    }

    @GetMapping("/rounds")
    public String rounds(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        // M-11: page/size는 사용자가 URL 쿼리 파라미터로 직접 조작할 수 있다 — 음수 page나
        // 0/음수 size를 클램프 없이 PageRequest.of에 넘기면 IllegalArgumentException(500)이
        // 된다. HTML 관리자 화면이라 400 거부 대신 안전한 값으로 조용히 클램프한다.
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), ROUNDS_MAX_PAGE_SIZE);
        PageResponse<WinningNumberResponse> list = queryService.list(clampedPage, clampedSize);
        var items = list.items().stream().map(AdminRoundView::from).toList();
        model.addAttribute("rounds", new AdminRoundPageView(items, list.page(), list.totalElements(), list.totalPages()));
        return "admin/rounds";
    }

    // FE-094: 이전에는 /rounds/collect 하나가 optional round로 "최신 수집"과 "특정 회차 수집"을
    // 겸했다. 두 버튼이 같은 엔드포인트를 쓰고 입력에 required가 없어, 회차를 비운 채 "특정 회차
    // 수집"을 누르면 round=null이 되어 조용히 최신 수집이 실행됐다. 버튼 라벨과 실제 동작이
    // 달라지는 운영 사고 경로라, 명령별로 엔드포인트를 나누고 회차를 필수로 만든다.
    @PostMapping("/rounds/collect-latest")
    public String collectLatest(@AuthenticationPrincipal UserDetails user,
                                HttpServletRequest req,
                                RedirectAttributes redirect) {
        try {
            var resp = collectionService.collectLatest();
            auditLogService.record(user.getUsername(), "COLLECT_LATEST",
                    "round=" + resp.round(), null, ipResolver.resolve(req));
            redirect.addFlashAttribute("success", resp.round() + "회차 기준 최신 상태 확인 완료");
        } catch (Exception e) {
            log.warn("관리자 최신 수집 실패", e);
            redirect.addFlashAttribute("error", "수집 실패: " + e.getMessage());
        }
        return "redirect:/admin/rounds";
    }

    @PostMapping("/rounds/collect-round")
    public String collectRound(@RequestParam Integer round,
                               @AuthenticationPrincipal UserDetails user,
                               HttpServletRequest req,
                               RedirectAttributes redirect) {
        if (round == null || round < 1) {
            redirect.addFlashAttribute("error", "수집할 회차 번호를 1 이상으로 입력하세요.");
            return "redirect:/admin/rounds";
        }
        try {
            collectionService.collectRound(round);
            auditLogService.record(user.getUsername(), "COLLECT_ROUND",
                    "round=" + round, null, ipResolver.resolve(req));
            redirect.addFlashAttribute("success", round + "회차 수집 완료");
        } catch (Exception e) {
            log.warn("관리자 회차 수집 실패: round={}", round, e);
            redirect.addFlashAttribute("error", "수집 실패: " + e.getMessage());
        }
        return "redirect:/admin/rounds";
    }

    @PostMapping("/rounds/collect-all")
    public String collectAll(@AuthenticationPrincipal UserDetails user,
                             HttpServletRequest req,
                             RedirectAttributes redirect) {
        // isRunning() 체크 후 별도로 backfillAllAsync()를 호출하면 그 사이(TOCTOU) 두 요청이 동시에
        // "실행 중 아님"을 볼 수 있다. tryStart()의 CAS가 시작 예약 자체를 원자적으로 만든다.
        if (!backfillService.tryStart()) {
            redirect.addFlashAttribute("error", "전체 회차 수집이 이미 진행 중입니다. 완료 후 다시 시도하세요.");
            return "redirect:/admin/rounds";
        }
        try {
            backfillService.backfillAllAsync();
        } catch (TaskRejectedException e) {
            // 실행자 큐가 가득 찬 극단적 상황 — tryStart로 예약한 running 플래그를 되돌리지 않으면
            // 이후 모든 요청이 영구히 "진행 중"으로 잘못 거부된다.
            backfillService.releaseStart();
            redirect.addFlashAttribute("error", "전체 회차 수집 시작에 실패했습니다. 잠시 후 다시 시도하세요.");
            return "redirect:/admin/rounds";
        }
        auditLogService.record(user.getUsername(), "COLLECT_ALL",
                "전체 회차 수집 시작", null, ipResolver.resolve(req));
        redirect.addFlashAttribute("success",
                "전체 회차 수집을 백그라운드에서 시작했습니다. 수 분이 소요되며, 잠시 후 회차 목록을 새로고침하세요.");
        return "redirect:/admin/rounds";
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "50") int size,
                        Model model) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), AUDIT_MAX_PAGE_SIZE);
        model.addAttribute("logs", auditLogService.findAll(PageRequest.of(clampedPage, clampedSize)));
        return "admin/audit";
    }
}
