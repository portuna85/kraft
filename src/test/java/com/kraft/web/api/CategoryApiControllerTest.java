package com.kraft.web.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.service.CategoryService;
import com.kraft.service.PostService;
import com.kraft.web.dto.category.CategoryResponseDto;
import com.kraft.web.dto.category.CategorySaveRequestDto;
import com.kraft.web.dto.category.CategoryUpdateRequestDto;
import com.kraft.web.dto.common.PageResponse;
import com.kraft.web.dto.post.PostsListResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 카테고리 API 컨트롤러 테스트
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = CategoryApiController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration.class}))
@AutoConfigureMockMvc(addFilters = false)
class CategoryApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private PostService postService;

    @Test
    @DisplayName("모든 카테고리 조회에 성공한다 - 인증 없이")
    void getAllCategories() throws Exception {
        // given
        CategoryResponseDto category1 = new CategoryResponseDto(1L, "공지사항", "공지사항", 0);
        CategoryResponseDto category2 = new CategoryResponseDto(2L, "일반", "일반 게시글", 1);

        given(categoryService.findAll()).willReturn(Arrays.asList(category1, category2));

        // expect
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("공지사항"))
                .andExpect(jsonPath("$[1].name").value("일반"));
    }

    @Test
    @DisplayName("카테고리 단건 조회에 성공한다 - 인증 없이")
    void getCategory() throws Exception {
        // given
        CategoryResponseDto category = new CategoryResponseDto(1L, "공지사항", "공지사항", 0);

        given(categoryService.findById(1L)).willReturn(category);

        // expect
        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("공지사항"));
    }

    @Test
    @DisplayName("카테고리를 생성할 수 있다")
    void createCategory() throws Exception {
        // given
        CategorySaveRequestDto requestDto = CategorySaveRequestDto.builder()
                .name("신규카테고리")
                .description("신규 카테고리")
                .displayOrder(5)
                .build();

        given(categoryService.save(any(CategorySaveRequestDto.class))).willReturn(5L);

        // expect
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().string("5"));
    }

    @Test
    @DisplayName("카테고리를 수정할 수 있다")
    void updateCategory() throws Exception {
        // given
        CategoryUpdateRequestDto requestDto = CategoryUpdateRequestDto.builder()
                .name("수정된카테고리")
                .description("수정된 설명")
                .displayOrder(1)
                .build();

        given(categoryService.update(eq(1L), any(CategoryUpdateRequestDto.class))).willReturn(1L);

        // expect
        mockMvc.perform(put("/api/v1/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("카테고리를 삭제할 수 있다")
    void deleteCategory() throws Exception {
        // expect
        mockMvc.perform(delete("/api/v1/categories/1"))
                .andExpect(status().isNoContent());
    }


    @Test
    @DisplayName("카테고리별 게시글 조회에 성공한다 - 인증 없이")
    void getPostsByCategory() throws Exception {
        // given
        PostsListResponseDto post = new PostsListResponseDto(
                1L, "Test Post", "author", 10L, LocalDateTime.now()
        );

        PageResponse<PostsListResponseDto> pageResponse = PageResponse.of(
                List.of(post), 0, 10, 1, 1
        );

        given(postService.findByCategoryId(1L, 0, 10)).willReturn(pageResponse);

        // expect
        mockMvc.perform(get("/api/v1/categories/1/posts")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Test Post"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}

