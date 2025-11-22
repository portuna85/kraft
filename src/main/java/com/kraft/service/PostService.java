package com.kraft.service;

import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.common.PageResponse;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import com.kraft.common.exception.ResourceNotFoundException;
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
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long save(PostSaveRequestDto requestDto, SessionUser sessionUser) {
        User author = findUserById(sessionUser.id());
        Post post = requestDto.toEntity(author);

        author.addPost(post);
        Post savedPost = postRepository.save(post);

        log.info("게시글 작성 성공: postId={}, authorId={}", savedPost.getId(), author.getId());
        return savedPost.getId();
    }

    @Transactional
    public Long update(Long id, PostUpdateRequestDto requestDto) {
        Post post = findPostById(id);
        post.update(requestDto.getTitle(), requestDto.getContent());

        log.info("게시글 수정 성공: postId={}", id);
        return id;
    }

    @Transactional
    public void delete(Long id) {
        Post post = findPostById(id);
        postRepository.delete(post);

        log.info("게시글 삭제 성공: postId={}", id);
    }

    @Transactional(readOnly = true)
    public PostResponseDto findById(Long id) {
        Post post = findPostById(id);
        return PostResponseDto.from(post);
    }

    /**
     * 게시글 조회 (조회수 증가)
     * @param id 게시글 ID
     * @return 게시글 응답 DTO
     */
    @Transactional
    public PostResponseDto findByIdAndIncrementView(Long id) {
        Post post = findPostById(id);
        post.incrementViewCount();

        log.debug("게시글 조회수 증가: postId={}, viewCount={}", id, post.getViewCount());
        return PostResponseDto.from(post);
    }

    @Transactional(readOnly = true)
    public List<PostsListResponseDto> findAllDesc() {
        return postRepository.findAllDesc().stream()
                .map(PostsListResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 페이지네이션으로 게시글 목록 조회
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @param sortBy 정렬 기준 (id, createAt, updateAt)
     * @param direction 정렬 방향 (ASC, DESC)
     * @return 페이지네이션 응답
     */
    @Transactional(readOnly = true)
    public PageResponse<PostsListResponseDto> findAllWithPagination(
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<Post> postPage = postRepository.findAllWithAuthor(pageable);

        List<PostsListResponseDto> content = postPage.getContent().stream()
                .map(PostsListResponseDto::from)
                .collect(Collectors.toList());

        log.debug("게시글 페이지 조회: page={}, size={}, totalElements={}",
                page, size, postPage.getTotalElements());

        return PageResponse.of(
                content,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages()
        );
    }

    /**
     * 특정 사용자의 게시글 목록 조회
     * @param authorId 작성자 ID
     * @return 게시글 목록
     */
    @Transactional(readOnly = true)
    public List<PostsListResponseDto> findByAuthorId(Long authorId) {
        return postRepository.findByAuthorId(authorId).stream()
                .map(PostsListResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 제목 또는 내용으로 게시글 검색
     * @param keyword 검색 키워드
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 검색 결과 페이지
     */
    @Transactional(readOnly = true)
    public PageResponse<PostsListResponseDto> searchPosts(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Post> postPage = postRepository.searchByTitleOrContent(keyword, pageable);

        List<PostsListResponseDto> content = postPage.getContent().stream()
                .map(PostsListResponseDto::from)
                .collect(Collectors.toList());

        log.debug("게시글 검색: keyword={}, page={}, totalElements={}",
                keyword, page, postPage.getTotalElements());

        return PageResponse.of(
                content,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages()
        );
    }

    /**
     * 인기 게시글 조회 (조회수 기준)
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 인기 게시글 페이지
     */
    @Transactional(readOnly = true)
    public PageResponse<PostsListResponseDto> findPopularPosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> postPage = postRepository.findPopularPosts(pageable);

        List<PostsListResponseDto> content = postPage.getContent().stream()
                .map(PostsListResponseDto::from)
                .collect(Collectors.toList());

        log.debug("인기 게시글 조회: page={}, size={}, totalElements={}",
                page, size, postPage.getTotalElements());

        return PageResponse.of(
                content,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages()
        );
    }

    /**
     * 카테고리별 게시글 조회
     * @param categoryId 카테고리 ID
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 게시글 페이지
     */
    @Transactional(readOnly = true)
    public PageResponse<PostsListResponseDto> findByCategoryId(Long categoryId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Post> postPage = postRepository.findByCategoryId(categoryId, pageable);

        List<PostsListResponseDto> content = postPage.getContent().stream()
                .map(PostsListResponseDto::from)
                .collect(Collectors.toList());

        log.debug("카테고리별 게시글 조회: categoryId={}, page={}, totalElements={}",
                categoryId, page, postPage.getTotalElements());

        return PageResponse.of(
                content,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages()
        );
    }

    private Post findPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("게시글", id));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
    }
}
