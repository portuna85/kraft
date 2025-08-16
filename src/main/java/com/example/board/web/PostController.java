package com.example.board.web;

import com.example.board.member.Member;
import com.example.board.member.MemberRepository;
import com.example.board.post.Post;
import com.example.board.post.PostService;
import com.example.board.post.CommentService;
import com.example.board.post.CommentRepository;
import com.example.board.file.AttachmentService;
import com.example.board.file.AttachmentRepository;
import com.example.board.file.Attachment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;
    private final MemberRepository memberRepository;
    private final AttachmentService attachmentService;
    private final AttachmentRepository attachmentRepository;
    private final CommentService commentService;
    private final CommentRepository commentRepository;

    public PostController(PostService postService,
                          MemberRepository memberRepository,
                          AttachmentService attachmentService,
                          AttachmentRepository attachmentRepository,
                          CommentService commentService,
                          CommentRepository commentRepository) {
        this.postService = postService;
        this.memberRepository = memberRepository;
        this.attachmentService = attachmentService;
        this.attachmentRepository = attachmentRepository;
        this.commentService = commentService;
        this.commentRepository = commentRepository;
    }

    public static class PostForm {
        @NotBlank(message = "제목을 입력하세요.")
        private String title;
        @NotBlank(message = "내용을 입력하세요.")
        private String content;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    private Member getCurrentMemberOr401(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }

    /**
     * 목록
     */
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Post> result = postService.list(q, PageRequest.of(page, 10, Sort.by("id").descending()));
        model.addAttribute("page", result);
        model.addAttribute("q", q);
        return "posts/list";
    }

    /**
     * 작성 폼
     */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new PostForm());
        return "posts/form";
    }

    /**
     * 작성 처리 + 첨부 업로드
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("form") PostForm form,
                         org.springframework.validation.BindingResult bindingResult,
                         @RequestParam(value = "files", required = false) MultipartFile[] files,
                         @AuthenticationPrincipal(expression = "username") String email,
                         Model model) {
        if (bindingResult.hasErrors()) {
            return "posts/form";
        }
        Member me = getCurrentMemberOr401(email);

        Post created = postService.write(form.getTitle(), form.getContent(), me);

        // 파일 업로드 처리 (선택)
        if (files != null) {
            Arrays.stream(files)
                    .filter(f -> f != null && !f.isEmpty())
                    .forEach(f -> {
                        Attachment att = attachmentService.store(f, created);
                        if (att != null) {
                            attachmentRepository.save(att);
                        }
                    });
        }

        return "redirect:/posts/" + created.getId();
    }

    /**
     * 상세 (댓글 페이징 + 대댓글 개수 맵 포함)
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "0") int cpage,
                         Model model) {
        Post post = postService.getById(id);
        model.addAttribute("post", post);

        // 상위 댓글 페이지 (기본 createdAt ASC)
        PageRequest pr = PageRequest.of(cpage, 10, Sort.by(Sort.Direction.ASC, "createdAt"));
        var roots = commentService.listRoot(id, pr);
        model.addAttribute("commentsPage", roots);
        model.addAttribute("cpage", cpage);

        // 각 상위댓글의 대댓글 수
        Map<Long, Long> childCounts = new HashMap<>();
        roots.forEach(c -> childCounts.put(c.getId(), commentRepository.countByParentId(c.getId())));
        model.addAttribute("childCounts", childCounts);

        return "posts/detail";
    }

    /**
     * 수정 폼
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Post post = postService.getById(id);
        PostForm form = new PostForm();
        form.setTitle(post.getTitle());
        form.setContent(post.getContent());
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        return "posts/form";
    }

    /**
     * 수정 처리
     */
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute("form") PostForm form,
                       org.springframework.validation.BindingResult bindingResult,
                       @AuthenticationPrincipal(expression = "username") String email,
                       Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("id", id);
            return "posts/form";
        }
        Member me = getCurrentMemberOr401(email);
        postService.edit(id, form.getTitle(), form.getContent(), me);
        return "redirect:/posts/" + id;
    }

    /**
     * 삭제
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal(expression = "username") String email) {
        Member me = getCurrentMemberOr401(email);
        postService.delete(id, me);
        return "redirect:/posts";
    }
}
