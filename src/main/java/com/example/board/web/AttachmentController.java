package com.example.board.web;

import com.example.board.file.AttachmentService;
import com.example.board.member.Member;
import com.example.board.member.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final MemberRepository memberRepository;

    public AttachmentController(AttachmentService attachmentService,
                                MemberRepository memberRepository) {
        this.attachmentService = attachmentService;
        this.memberRepository = memberRepository;
    }

    private Member getCurrentMemberOr401(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }

    /**
     * 첨부 삭제 (작성자 or ADMIN)
     */
    @PostMapping("/attachments/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam Long postId,
                         @AuthenticationPrincipal(expression = "username") String email) {
        Member me = getCurrentMemberOr401(email);
        // 서비스 시그니처: delete(Long attachmentId, Member requester)
        attachmentService.delete(id, me);
        return "redirect:/posts/" + postId;
    }
}
