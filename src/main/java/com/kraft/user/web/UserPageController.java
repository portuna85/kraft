package com.kraft.user.web;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.config.security.UserDetailsServiceImpl;
import com.kraft.user.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RequiredArgsConstructor
@Controller
public class UserPageController {

    private final EmailVerificationService emailVerificationService;
    private final UserDetailsServiceImpl userDetailsService;

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("pageTitle", "회원가입");
        return "user/signup";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "로그인");
        return "user/login";
    }

    /** 비밀번호 재설정 링크를 요청하는 화면. 로그인 화면에서 들어온다. */
    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("pageTitle", "비밀번호 찾기");
        return "user/forgot-password";
    }

    /**
     * 메일의 링크가 여는 화면. 토큰은 여기서 검사하지 않는다 — 화면을 그리는 것만으로 토큰을
     * 쓴 셈이 되면 안 되고(메일 미리보기·링크 검사기가 대신 눌러 버린다), 판정은 새 비밀번호와
     * 함께 오는 저장 요청에서 한 번만 한다.
     */
    @GetMapping("/users/password-reset")
    public String passwordReset(@RequestParam String token, Model model) {
        model.addAttribute("resetToken", token);
        model.addAttribute("pageTitle", "비밀번호 재설정");
        return "user/password-reset";
    }

    /**
     * 메일의 인증 링크가 여는 화면. 여기서는 토큰을 소비하지 않는다 — 화면을 그리는 것만으로
     * 인증이 끝나 버리면, 사용자보다 먼저 링크를 여는 메일 보안 스캐너·미리보기가 토큰을 대신
     * 써 버린다(전체 리뷰 2026-09-26 A-FE-04). "이메일 인증 완료하기" 버튼을 누른 사용자의
     * 명시적 POST에서만 실제로 소비한다.
     */
    @GetMapping("/users/verify")
    public String verifyEmailConfirm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("pageTitle", "이메일 인증");
        return "user/verify-confirm";
    }

    /**
     * 확인 화면의 버튼이 제출하는 실제 인증 요청. 결과를 flash attribute로 실어
     * {@code GET /users/verify/result}로 리다이렉트한다(PRG) — 그래야 인증 직후 새로고침해도
     * "이미 사용된 링크"로 다시 제출되지 않는다.
     */
    @PostMapping("/users/verify")
    public String verifyEmailSubmit(@RequestParam String token, Authentication authentication,
                                     HttpServletRequest request, HttpServletResponse response,
                                     RedirectAttributes redirectAttributes) {
        try {
            Long verifiedUserId = emailVerificationService.verify(token);
            refreshSessionAuthoritiesIfSameAccount(authentication, verifiedUserId, request, response);
            redirectAttributes.addFlashAttribute("success", true);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("success", false);
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/users/verify/result";
    }

    @GetMapping("/users/verify/result")
    public String verifyEmailResult(Model model) {
        // flash attribute 없이 직접 들어온 경우(북마크·새로고침) — 판정할 근거가 없다.
        if (!model.containsAttribute("success")) {
            model.addAttribute("success", false);
            model.addAttribute("message", "인증 처리 결과를 확인할 수 없습니다. 인증 메일의 링크를 다시 열어 주세요.");
        }
        model.addAttribute("pageTitle", "이메일 인증");
        return "user/verify-result";
    }

    /**
     * 인증 성공이 지금 이 요청의 세션 권한에도 곧바로 반영되게 한다(전체 리뷰 2026-09-26
     * A-BE-08) — 그러지 않으면 로그인한 GUEST가 이 화면에서 인증을 마쳐도 "인증 메일
     * 재발송" 같은 GUEST 전용 메뉴가 로그아웃 전까지 계속 보인다.
     * <p>
     * 지금 세션이 방금 승격된 바로 그 계정일 때만 갱신한다 — 다른 기기·다른 계정의 세션은
     * 건드리지 않고, 다음 로그인 때 자연히 반영된다(현행 유지).
     */
    private void refreshSessionAuthoritiesIfSameAccount(Authentication authentication, Long verifiedUserId,
                                                         HttpServletRequest request, HttpServletResponse response) {
        if (!(authentication != null && authentication.getPrincipal() instanceof KraftUserDetails current)
                || !current.getUserId().equals(verifiedUserId)) {
            return;
        }
        UserDetails refreshed = userDetailsService.loadUserById(verifiedUserId);
        Authentication newAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                refreshed, authentication.getCredentials(), refreshed.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuthentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
