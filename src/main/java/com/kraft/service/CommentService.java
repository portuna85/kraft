package com.kraft.service;

import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.comment.Comment;
import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.comment.CommentResponseDto;
import com.kraft.web.dto.comment.CommentSaveRequestDto;
import com.kraft.web.dto.comment.CommentUpdateRequestDto;
import com.kraft.web.dto.common.PageResponse;
import com.kraft.common.exception.ResourceNotFoundException;
import com.kraft.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 댓글 작성
     * @param postId 게시글 ID
     * @param requestDto 댓글 작성 요청 DTO
     * @param sessionUser 작성자
     * @return 생성된 댓글 ID
     */
    @Transactional
    public Long save(Long postId, CommentSaveRequestDto requestDto, SessionUser sessionUser) {
        Post post = findPostById(postId);
        User author = findUserById(sessionUser.id());

        Comment comment = Comment.builder()
                .content(requestDto.getContent())
                .post(post)
                .author(author)
                .build();

        Comment savedComment = commentRepository.save(comment);

        log.info("댓글 작성 성공: commentId={}, postId={}, authorId={}",
                savedComment.getId(), postId, author.getId());

        return savedComment.getId();
    }

    /**
     * 대댓글(답글) 작성
     * @param postId 게시글 ID
     * @param parentId 부모 댓글 ID
     * @param requestDto 댓글 작성 요청 DTO
     * @param sessionUser 작성자
     * @return 생성된 답글 ID
     */
    @Transactional
    public Long saveReply(Long postId, Long parentId, CommentSaveRequestDto requestDto, SessionUser sessionUser) {
        Post post = findPostById(postId);
        Comment parentComment = findCommentById(parentId);
        User author = findUserById(sessionUser.id());

        // 부모 댓글이 같은 게시글의 댓글인지 확인
        if (!parentComment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다");
        }

        Comment reply = Comment.builder()
                .content(requestDto.getContent())
                .post(post)
                .author(author)
                .parent(parentComment)
                .build();

        parentComment.addReply(reply);
        Comment savedReply = commentRepository.save(reply);

        log.info("답글 작성 성공: replyId={}, parentId={}, postId={}",
                savedReply.getId(), parentId, postId);

        return savedReply.getId();
    }

    /**
     * 댓글 수정
     * @param commentId 댓글 ID
     * @param requestDto 댓글 수정 요청 DTO
     * @param sessionUser 수정 요청자
     * @return 수정된 댓글 ID
     */
    @Transactional
    public Long update(Long commentId, CommentUpdateRequestDto requestDto, SessionUser sessionUser) {
        Comment comment = findCommentById(commentId);

        // 작성자 본인 확인
        if (!comment.isAuthor(sessionUser.id())) {
            throw new UnauthorizedException("댓글 작성자만 수정할 수 있습니다");
        }

        comment.update(requestDto.getContent());

        log.info("댓글 수정 성공: commentId={}", commentId);
        return commentId;
    }

    /**
     * 댓글 삭제
     * @param commentId 댓글 ID
     * @param sessionUser 삭제 요청자
     */
    @Transactional
    public void delete(Long commentId, SessionUser sessionUser) {
        Comment comment = findCommentById(commentId);

        // 작성자 본인 확인
        if (!comment.isAuthor(sessionUser.id())) {
            throw new UnauthorizedException("댓글 작성자만 삭제할 수 있습니다");
        }

        commentRepository.delete(comment);

        log.info("댓글 삭제 성공: commentId={}", commentId);
    }

    /**
     * 특정 게시글의 댓글 목록 조회
     * @param postId 게시글 ID
     * @return 댓글 목록
     */
    @Transactional(readOnly = true)
    public List<CommentResponseDto> findByPostId(Long postId) {
        // 게시글 존재 확인
        findPostById(postId);

        return commentRepository.findByPostIdWithAuthor(postId).stream()
                .map(CommentResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 게시글의 부모 댓글 목록 조회 (대댓글 제외)
     * @param postId 게시글 ID
     * @return 부모 댓글 목록
     */
    @Transactional(readOnly = true)
    public List<CommentResponseDto> findParentCommentsByPostId(Long postId) {
        findPostById(postId);

        return commentRepository.findParentCommentsByPostId(postId).stream()
                .map(CommentResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 게시글의 부모 댓글 페이징 조회
     * @param postId 게시글 ID
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 부모 댓글 페이지
     */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponseDto> findParentCommentsWithPagination(
            Long postId,
            int page,
            int size
    ) {
        findPostById(postId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Comment> commentPage = commentRepository.findParentCommentsByPostId(postId, pageable);

        List<CommentResponseDto> content = commentPage.getContent().stream()
                .map(CommentResponseDto::from)
                .collect(Collectors.toList());

        log.debug("부모 댓글 페이지 조회: postId={}, page={}, totalElements={}",
                postId, page, commentPage.getTotalElements());

        return PageResponse.of(
                content,
                commentPage.getNumber(),
                commentPage.getSize(),
                commentPage.getTotalElements(),
                commentPage.getTotalPages()
        );
    }

    /**
     * 특정 댓글의 답글 목록 조회
     * @param parentId 부모 댓글 ID
     * @return 답글 목록
     */
    @Transactional(readOnly = true)
    public List<CommentResponseDto> findRepliesByParentId(Long parentId) {
        // 부모 댓글 존재 확인
        findCommentById(parentId);

        return commentRepository.findRepliesByParentId(parentId).stream()
                .map(CommentResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 사용자의 댓글 목록 조회
     * @param authorId 작성자 ID
     * @return 댓글 목록
     */
    @Transactional(readOnly = true)
    public List<CommentResponseDto> findByAuthorId(Long authorId) {
        return commentRepository.findByAuthorIdWithPost(authorId).stream()
                .map(CommentResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 게시글의 댓글 수 조회
     * @param postId 게시글 ID
     * @return 댓글 수
     */
    @Transactional(readOnly = true)
    public long countByPostId(Long postId) {
        return commentRepository.countByPostId(postId);
    }

    private Comment findCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("댓글", commentId));
    }

    private Post findPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("게시글", postId));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
    }
}
