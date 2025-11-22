package com.kraft.service;

import com.kraft.domain.category.Category;
import com.kraft.domain.category.CategoryRepository;
import com.kraft.web.dto.category.CategoryResponseDto;
import com.kraft.web.dto.category.CategorySaveRequestDto;
import com.kraft.web.dto.category.CategoryUpdateRequestDto;
import com.kraft.common.exception.DuplicateResourceException;
import com.kraft.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 모든 카테고리 조회
     * @return 카테고리 목록 (정렬 순서대로)
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> findAll() {
        return categoryRepository.findAllOrderByDisplayOrder().stream()
                .map(CategoryResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * ID로 카테고리 조회
     * @param id 카테고리 ID
     * @return 카테고리 응답 DTO
     */
    @Transactional(readOnly = true)
    public CategoryResponseDto findById(Long id) {
        Category category = findCategoryById(id);
        return CategoryResponseDto.from(category);
    }

    /**
     * 이름으로 카테고리 조회
     * @param name 카테고리명
     * @return 카테고리 응답 DTO
     */
    @Transactional(readOnly = true)
    public CategoryResponseDto findByName(String name) {
        Category category = categoryRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("카테고리", "name", name));
        return CategoryResponseDto.from(category);
    }

    /**
     * 카테고리 생성 (관리자 전용)
     * @param requestDto 카테고리 생성 요청 DTO
     * @return 생성된 카테고리 ID
     */
    @Transactional
    public Long save(CategorySaveRequestDto requestDto) {
        // 중복 이름 확인
        if (categoryRepository.existsByName(requestDto.getName())) {
            throw new DuplicateResourceException("이미 존재하는 카테고리명입니다: " + requestDto.getName());
        }

        Category category = Category.builder()
                .name(requestDto.getName())
                .description(requestDto.getDescription())
                .displayOrder(requestDto.getDisplayOrder())
                .build();

        Category savedCategory = categoryRepository.save(category);
        log.info("카테고리 생성 성공: categoryId={}, name={}", savedCategory.getId(), savedCategory.getName());

        return savedCategory.getId();
    }

    /**
     * 카테고리 수정 (관리자 전용)
     * @param id 카테고리 ID
     * @param requestDto 카테고리 수정 요청 DTO
     * @return 수정된 카테고리 ID
     */
    @Transactional
    public Long update(Long id, CategoryUpdateRequestDto requestDto) {
        Category category = findCategoryById(id);

        // 이름이 변경된 경우 중복 확인
        if (!category.getName().equals(requestDto.getName())) {
            if (categoryRepository.existsByName(requestDto.getName())) {
                throw new DuplicateResourceException("이미 존재하는 카테고리명입니다: " + requestDto.getName());
            }
        }

        category.update(requestDto.getName(), requestDto.getDescription(), requestDto.getDisplayOrder());
        log.info("카테고리 수정 성공: categoryId={}", id);

        return id;
    }

    /**
     * 카테고리 삭제 (관리자 전용)
     * @param id 카테고리 ID
     */
    @Transactional
    public void delete(Long id) {
        Category category = findCategoryById(id);
        categoryRepository.delete(category);
        log.info("카테고리 삭제 성공: categoryId={}", id);
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("카테고리", id));
    }
}
