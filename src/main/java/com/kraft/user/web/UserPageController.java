package com.kraft.user.web;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.config.security.UserDetailsServiceImpl;
import com.kraft.user.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
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

    /** 상태 없는 객체라 재사용한다. */
    private static final HttpSessionSecurityContextRepository SESSION_CONTEXT_REPOSITORY =
            new HttpSessionSecurityContextRepository();

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
     * 메일의 링크가 여는 화면. 토큰은 검사하지 않는다 — 그리는 것만으로 소비되면 메일 미리보기·링크 검사기가 대신
     * 써 버린다. 토큰은 URL 프래그먼트라 서버로 오지 않으며, 화면(password-reset/mount.js)이 {@code location.hash}에서 읽는다.
     */
    @GetMapping("/users/password-reset")
    public String passwordReset(Model model) {
        model.addAttribute("pageTitle", "비밀번호 재설정");
        return "user/password-reset";
    }

    /**
     * 메일의 인증 링크가 여는 화면. 토큰을 소비하지 않는다 — 그리는 것만으로 인증되면 메일 보안 스캐너가 대신 써
     * 버린다. 사용자가 버튼을 눌러 보내는 POST에서만 소비한다. 새 메일의 토큰은 프래그먼트라 화면
     * ({@code verify-confirm.js})이 읽어 폼에 채우고, 쿼리 문자열은 옛 메일 링크(24시간 유효)를 위한 하위 호환이다.
     */
    @GetMapping("/users/verify")
    public String verifyEmailConfirm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        model.addAttribute("pageTitle", "이메일 인증");
        return "user/verify-confirm";
    }

    /** 인증 요청. 결과를 flash attribute로 실어 결과 화면으로 리다이렉트한다(PRG) — 새로고침해도 다시 제출되지 않는다. */
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
        // flash 없이 직접 들어온 경우(북마크·새로고침)는 판정할 근거가 없다.
        if (!model.containsAttribute("success")) {
            model.addAttribute("success", false);
            model.addAttribute("message", "인증 처리 결과를 확인할 수 없습니다. 인증 메일의 링크를 다시 열어 주세요.");
        }
        model.addAttribute("pageTitle", "이메일 인증");
        return "user/verify-result";
    }

    /**
     * 인증 성공을 지금 요청의 세션 권한에도 곧바로 반영한다(아니면 GUEST 전용 메뉴가 로그아웃 전까지 남는다).
     * 세션이 방금 승격된 바로 그 계정일 때만 갱신하고, 다른 기기·계정은 다음 로그인 때 반영된다.
     */
    private void refreshSessionAuthoritiesIfSameAccount(Authentication authentication, Long verifiedUserId,
                                                         HttpServletRequest request, HttpServletResponse response) {
        if (!(authentication != null && authentication.getPrincipal() instanceof KraftUserDetails current)
                || !current.getUserId().equals(verifiedUserId)) {
            return;
        }
        UserDetails refreshed = userDetailsService.loadUserById(verifiedUserId);
        // 일반 로그인과 달리 이 경로는 credentials를 지우지 않으므로, 지우지 않으면 BCrypt 해시가 세션 테이블에 직렬화된다.
        if (refreshed instanceof CredentialsContainer container) {
            container.eraseCredentials();
        }
        Authentication newAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                refreshed, null, refreshed.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuthentication);
        SecurityContextHolder.setContext(context);
        SESSION_CONTEXT_REPOSITORY.saveContext(context, request, response);
    }
}
