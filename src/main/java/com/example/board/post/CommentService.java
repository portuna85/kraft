package com.example.board.post;

import com.example.board.member.Member;
import com.example.board.member.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    public Comment write(Long postId, Member author, String content) {
        if (author == null || author.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력하세요.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));
        return commentRepository.save(new Comment(post, author, content.trim()));
    }

    public Comment reply(Long postId, Long parentId, Member author, String content) {
        if (author == null || author.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력하세요.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));

        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부모 댓글을 찾을 수 없습니다."));

        if (!parent.getPost().getId().equals(post.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "부모 댓글이 해당 게시글에 속하지 않습니다.");
        }
        // 0(루트) → 1(대댓글)까지만 허용
        if (parent.getDepth() >= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대댓글은 2단계까지만 허용됩니다.");
        }

        return commentRepository.save(new Comment(post, author, content.trim(), parent));
    }

    public void delete(Long commentId, Member requester) {
        Comment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));

        boolean isOwner = c.getAuthor() != null
                && c.getAuthor().getId() != null
                && c.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester != null && requester.getRole() == Role.ADMIN;

        if (!(isOwner || isAdmin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 댓글을 삭제할 수 있습니다.");
        }

        // 루트 댓글이면 자식들 먼저 정리(2단계 구조 가정).
        if (c.getParent() == null) {
            List<Comment> children = commentRepository.findByParentIdOrderByCreatedAtAsc(c.getId());
            if (!children.isEmpty()) {
                commentRepository.deleteAllInBatch(children);
            }
        }
        commentRepository.delete(c);
    }

    @Transactional(readOnly = true)
    public Page<Comment> listRoot(Long postId, Pageable pageable) {
        // 정렬이 비어 있으면 기본으로 createdAt ASC 적용
        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.ASC, "createdAt"));
        }
        return commentRepository.findByPostIdAndParentIsNull(postId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Comment> childrenOf(Long parentId) {
        return commentRepository.findByParentIdOrderByCreatedAtAsc(parentId);
    }

    // (선택) 카운트 유틸 — UI 뱃지/페이징에 활용
    @Transactional(readOnly = true)
    public long totalCount(Long postId) {
        return commentRepository.countByPostId(postId);
    }

    @Transactional(readOnly = true)
    public long rootCount(Long postId) {
        return commentRepository.countByPostIdAndParentIsNull(postId);
    }
}
