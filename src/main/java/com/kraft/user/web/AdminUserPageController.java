package com.kraft.user.web;

import com.kraft.shared.web.PageWindow;
import com.kraft.user.dto.SuspendedUserDto;
import com.kraft.user.service.SuspensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 관리자용 정지 회원 화면. 경로가 {@code /admin} 아래라 {@code SecurityConfig}가 관리자만
 * 들여보낸다 — 이 컨트롤러는 권한을 다시 판정하지 않는다.
 */
@RequiredArgsConstructor
@Controller
public class AdminUserPageController {

    private final SuspensionService suspensionService;

    @GetMapping("/admin/users")
    public String suspendedUsers(@PageableDefault(size = 20) Pageable pageable, Model model) {
        Page<SuspendedUserDto> users = suspensionService.findSuspended(pageable);

        // 정지가 풀릴수록 목록이 줄어든다 — PostPageController·AdminReportPageController와
        // 같은 이유로 범위를 넘는 page를 보정한다(F09).
        if (users.getTotalPages() > 0 && pageable.getPageNumber() >= users.getTotalPages()) {
            return "redirect:" + UriComponentsBuilder.fromPath("/admin/users")
                    .queryParam("page", users.getTotalPages() - 1)
                    .build()
                    .toUriString();
        }

        model.addAttribute("users", users.getContent());
        model.addAttribute("pageWindow", PageWindow.of(users.getNumber(), users.getTotalPages()));
        model.addAttribute("suspendedCount", users.getTotalElements());
        model.addAttribute("pageTitle", "정지 회원");
        return "admin/users";
    }
}
