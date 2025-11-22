package com.kraft.service;

import com.kraft.domain.category.Category;
import com.kraft.domain.category.CategoryRepository;
import com.kraft.web.dto.category.CategoryResponseDto;
import com.kraft.web.dto.category.CategorySaveRequestDto;
import com.kraft.web.dto.category.CategoryUpdateRequestDto;
import com.kraft.common.exception.DuplicateResourceException;
import com.kraft.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("모든 카테고리를 조회할 수 있다")
    void findAll() {
        // given
        Category category1 = Category.builder()
                .name("공지사항")
                .displayOrder(0)
                .build();
        Category category2 = Category.builder()
                .name("일반")
                .displayOrder(1)
                .build();

        given(categoryRepository.findAllOrderByDisplayOrder())
                .willReturn(Arrays.asList(category1, category2));

        // when
        List<CategoryResponseDto> result = categoryService.findAll();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("공지사항");
        assertThat(result.get(1).name()).isEqualTo("일반");
    }

    @Test
    @DisplayName("ID로 카테고리를 조회할 수 있다")
    void findById() {
        // given
        Category category = Category.builder()
                .name("질문")
                .description("질문 카테고리")
                .displayOrder(2)
                .build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        // when
        CategoryResponseDto result = categoryService.findById(1L);

        // then
        assertThat(result.name()).isEqualTo("질문");
        assertThat(result.description()).isEqualTo("질문 카테고리");
    }

    @Test
    @DisplayName("이름으로 카테고리를 조회할 수 있다")
    void findByName() {
        // given
        Category category = Category.builder()
                .name("자유")
                .displayOrder(3)
                .build();

        given(categoryRepository.findByName("자유")).willReturn(Optional.of(category));

        // when
        CategoryResponseDto result = categoryService.findByName("자유");

        // then
        assertThat(result.name()).isEqualTo("자유");
    }

    @Test
    @DisplayName("카테고리를 생성할 수 있다")
    void save() {
        // given
        CategorySaveRequestDto requestDto = CategorySaveRequestDto.builder()
                .name("신규")
                .description("신규 카테고리")
                .displayOrder(4)
                .build();

        Category savedCategory = Category.builder()
                .name("신규")
                .description("신규 카테고리")
                .displayOrder(4)
                .build();

        given(categoryRepository.existsByName("신규")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(savedCategory);

        // when
        Long categoryId = categoryService.save(requestDto);

        // then
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("중복된 이름으로 카테고리를 생성하면 예외가 발생한다")
    void save_duplicateName() {
        // given
        CategorySaveRequestDto requestDto = CategorySaveRequestDto.builder()
                .name("공지사항")
                .displayOrder(0)
                .build();

        given(categoryRepository.existsByName("공지사항")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> categoryService.save(requestDto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("이미 존재하는 카테고리명");
    }

    @Test
    @DisplayName("카테고리를 수정할 수 있다")
    void update() {
        // given
        Category category = Category.builder()
                .name("기존")
                .description("기존 설명")
                .displayOrder(1)
                .build();

        CategoryUpdateRequestDto requestDto = CategoryUpdateRequestDto.builder()
                .name("수정됨")
                .description("수정된 설명")
                .displayOrder(2)
                .build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(categoryRepository.existsByName("수정됨")).willReturn(false);

        // when
        Long updatedId = categoryService.update(1L, requestDto);

        // then
        assertThat(updatedId).isEqualTo(1L);
        assertThat(category.getName()).isEqualTo("수정됨");
        assertThat(category.getDescription()).isEqualTo("수정된 설명");
        assertThat(category.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("카테고리 수정 시 이름이 중복되면 예외가 발생한다")
    void update_duplicateName() {
        // given
        Category category = Category.builder()
                .name("기존")
                .displayOrder(1)
                .build();

        CategoryUpdateRequestDto requestDto = CategoryUpdateRequestDto.builder()
                .name("공지사항")
                .displayOrder(2)
                .build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(categoryRepository.existsByName("공지사항")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> categoryService.update(1L, requestDto))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("카테고리를 삭제할 수 있다")
    void delete() {
        // given
        Category category = Category.builder()
                .name("삭제할카테고리")
                .displayOrder(5)
                .build();

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        // when
        categoryService.delete(1L);

        // then
        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 조회하면 예외가 발생한다")
    void findById_notFound() {
        // given
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("카테고리");
    }
}

