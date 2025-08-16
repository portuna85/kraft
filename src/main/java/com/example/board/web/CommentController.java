package com.example.board.web;

import com.example.board.member.Member;
import com.example.board.member.MemberRepository;
import com.example.board.post.CommentService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping
public class CommentController {

    private final CommentService commentService;
    private final MemberRepository memberRepository;

    public CommentController(CommentService commentService, MemberRepository memberRepository) {
        this.commentService = commentService;
        this.memberRepository = memberRepository;
    }

    public static class CommentForm {
        @NotBlank
        public String content;
    }

    @PostMapping("/posts/{postId}/comments")
    public String create(@PathVariable Long postId,
                         @ModelAttribute("form") CommentForm form,
                         @AuthenticationPrincipal User user) {
        Member me = memberRepository.findByEmail(user.getUsername()).orElseThrow();
        commentService.write(postId, me, form.content);
        return "redirect:/posts/" + postId;
    }

    @PostMapping("/comments/{id}/delete")
    public String delete(@PathVariable Long id,
                         @org.springframework.web.bind.annotation.RequestParam Long postId,
                         @AuthenticationPrincipal User user) {
        Member me = memberRepository.findByEmail(user.getUsername()).orElseThrow();
        commentService.delete(id, me);
        return "redirect:/posts/" + postId;
    }


    @PostMapping("/posts/{postId}/comments/{parentId}/reply")
    public String reply(@PathVariable Long postId,
                        @PathVariable Long parentId,
                        @ModelAttribute("form") CommentForm form,
                        @AuthenticationPrincipal User user) {
        Member me = memberRepository.findByEmail(user.getUsername()).orElseThrow();
        commentService.reply(postId, parentId, me, form.content);
        return "redirect:/posts/" + postId;
    }
}