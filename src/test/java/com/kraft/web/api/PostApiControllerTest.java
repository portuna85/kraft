package com.kraft.web.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.user.User;
import com.kraft.service.PostService;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PostApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;

    @Test
    @DisplayName("게시글 작성에 성공한다")
    void createPost_success() throws Exception {
        // given
        User user = User.of("author", "encoded", "author@example.com");
        SessionUser sessionUser = new SessionUser(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", sessionUser);

        PostSaveRequestDto requestDto = PostSaveRequestDto.builder()
                .title("Test Title")
                .content("Test Content")
                .build();

        given(postService.save(any(PostSaveRequestDto.class), any(SessionUser.class))).willReturn(1L);

        // expect
        mockMvc.perform(post("/api/v1/posts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("게시글 수정에 성공한다")
    void updatePost_success() throws Exception {
        // given
        PostUpdateRequestDto requestDto = PostUpdateRequestDto.builder()
                .title("Updated Title")
                .content("Updated Content")
                .build();

        given(postService.update(anyLong(), any(PostUpdateRequestDto.class))).willReturn(1L);

        // expect
        mockMvc.perform(put("/api/v1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("게시글 삭제에 성공한다")
    void deletePost_success() throws Exception {
        // expect
        mockMvc.perform(delete("/api/v1/posts/1"))
                .andExpect(status().isNoContent());

        verify(postService).delete(1L);
    }

    @Test
    @DisplayName("게시글 단건 조회에 성공한다")
    void getPost_success() throws Exception {
        // given
        PostResponseDto responseDto = new PostResponseDto(
                1L,
                "Test Title",
                "Test Content",
                "author",
                1L
        );

        given(postService.findByIdAndIncrementView(1L)).willReturn(responseDto);

        // expect
        mockMvc.perform(get("/api/v1/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Test Title"))
                .andExpect(jsonPath("$.content").value("Test Content"))
                .andExpect(jsonPath("$.author").value("author"));
    }

    @Test
    @DisplayName("게시글 목록 조회에 성공한다")
    void getPostList_success() throws Exception {
        // given
        PostsListResponseDto dto1 = new PostsListResponseDto(
                2L,
                "Second Post",
                "author",
                0L,
                null
        );
        PostsListResponseDto dto2 = new PostsListResponseDto(
                1L,
                "First Post",
                "author",
                0L,
                null
        );

        given(postService.findAllDesc()).willReturn(Arrays.asList(dto1, dto2));

        // expect
        mockMvc.perform(get("/api/v1/posts/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[0].title").value("Second Post"))
                .andExpect(jsonPath("$[1].id").value(1L))
                .andExpect(jsonPath("$[1].title").value("First Post"));
    }
}

