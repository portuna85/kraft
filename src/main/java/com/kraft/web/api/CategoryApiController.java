package com.kraft.web.api;

import com.kraft.service.CategoryService;
import com.kraft.service.PostService;
import com.kraft.web.dto.category.CategoryResponseDto;
import com.kraft.web.dto.category.CategorySaveRequestDto;
import com.kraft.web.dto.category.CategoryUpdateRequestDto;
import com.kraft.web.dto.common.PageResponse;
import com.kraft.web.dto.post.PostsListResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 카테고리 API 컨트롤러
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryApiController {

    private final CategoryService categoryService;
    private final PostService postService;

    /**
     * 모든 카테고리 조회
     * GET /api/v1/categories
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponseDto>> getAllCategories() {
        List<CategoryResponseDto> categories = categoryService.findAll();
        return ResponseEntity.ok(categories);
    }

    /**
     * ID로 카테고리 조회
     * GET /api/v1/categories/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDto> getCategory(@PathVariable Long id) {
        CategoryResponseDto category = categoryService.findById(id);
        return ResponseEntity.ok(category);
    }

    /**
     * 카테고리 생성 (관리자 전용)
     * POST /api/v1/categories
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Long> createCategory(@RequestBody @Valid CategorySaveRequestDto requestDto) {
        Long categoryId = categoryService.save(requestDto);
        log.info("카테고리 생성 API 호출: categoryId={}, name={}", categoryId, requestDto.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryId);
    }

    /**
     * 카테고리 수정 (관리자 전용)
     * PUT /api/v1/categories/{id}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Long> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid CategoryUpdateRequestDto requestDto
    ) {
        Long updatedId = categoryService.update(id, requestDto);
        log.info("카테고리 수정 API 호출: categoryId={}", updatedId);
        return ResponseEntity.ok(updatedId);
    }

    /**
     * 카테고리 삭제 (관리자 전용)
     * DELETE /api/v1/categories/{id}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        log.info("카테고리 삭제 API 호출: categoryId={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 특정 카테고리의 게시글 목록 조회
     * GET /api/v1/categories/{id}/posts?page=0&size=10
     */
    @GetMapping("/{id}/posts")
    public ResponseEntity<PageResponse<PostsListResponseDto>> getPostsByCategory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PostsListResponseDto> response = postService.findByCategoryId(id, page, size);
        log.info("카테고리별 게시글 조회 API 호출: categoryId={}, results={}", id, response.totalElements());
        return ResponseEntity.ok(response);
    }
}

