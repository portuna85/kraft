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
        model.addAttribute("users", users.getContent());
        model.addAttribute("pageWindow", PageWindow.of(users.getNumber(), users.getTotalPages()));
        model.addAttribute("suspendedCount", users.getTotalElements());
        model.addAttribute("pageTitle", "정지 회원");
        return "admin/users";
    }
}
