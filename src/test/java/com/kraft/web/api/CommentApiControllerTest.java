package com.kraft.web.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.config.auth.LoginUserArgumentResolver;
import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.user.Role;
import com.kraft.service.CommentService;
import com.kraft.web.dto.comment.CommentResponseDto;
import com.kraft.web.dto.comment.CommentSaveRequestDto;
import com.kraft.web.dto.comment.CommentUpdateRequestDto;
import com.kraft.web.dto.common.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
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

@ActiveProfiles("test")
@WebMvcTest(controllers = CommentApiController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration.class}))
@AutoConfigureMockMvc(addFilters = false)
class CommentApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private LoginUserArgumentResolver loginUserArgumentResolver;

    private SessionUser sessionUser;

    @BeforeEach
    void setUp() throws Exception {
        sessionUser = new SessionUser(1L, "testUser", "test@example.com", Role.USER);
        given(loginUserArgumentResolver.supportsParameter(any())).willReturn(true);
        given(loginUserArgumentResolver.resolveArgument(any(), any(), any(), any())).willReturn(sessionUser);
    }

    @Test
    @DisplayName("댓글 작성에 성공한다")
    void createComment() throws Exception {
        // given
        CommentSaveRequestDto requestDto = CommentSaveRequestDto.builder()
                .content("Test Comment")
                .build();

        given(commentService.save(eq(1L), any(CommentSaveRequestDto.class), any(SessionUser.class)))
                .willReturn(1L);

        // expect
        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("답글 작성에 성공한다")
    void createReply() throws Exception {
        // given
        CommentSaveRequestDto requestDto = CommentSaveRequestDto.builder()
                .content("Test Reply")
                .build();

        given(commentService.saveReply(eq(1L), eq(2L), any(CommentSaveRequestDto.class), any(SessionUser.class)))
                .willReturn(3L);

        // expect
        mockMvc.perform(post("/api/v1/posts/1/comments/2/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().string("3"));
    }

    @Test
    @DisplayName("댓글 수정에 성공한다")
    void updateComment() throws Exception {
        // given
        CommentUpdateRequestDto requestDto = CommentUpdateRequestDto.builder()
                .content("Updated Comment")
                .build();

        given(commentService.update(eq(1L), any(CommentUpdateRequestDto.class), any(SessionUser.class)))
                .willReturn(1L);

        // expect
        mockMvc.perform(put("/api/v1/posts/1/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("댓글 삭제에 성공한다")
    void deleteComment() throws Exception {
        // expect
        mockMvc.perform(delete("/api/v1/posts/1/comments/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("게시글의 댓글 목록 조회에 성공한다")
    void getComments() throws Exception {
        // given
        CommentResponseDto comment1 = new CommentResponseDto(
                1L, "Comment 1", "author", 1L, null, 0, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        CommentResponseDto comment2 = new CommentResponseDto(
                2L, "Comment 2", "author", 1L, null, 0, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        given(commentService.findByPostId(1L)).willReturn(Arrays.asList(comment1, comment2));

        // expect
        mockMvc.perform(get("/api/v1/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("Comment 1"))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @DisplayName("부모 댓글만 조회에 성공한다")
    void getParentComments() throws Exception {
        // given
        CommentResponseDto comment = new CommentResponseDto(
                1L, "Parent Comment", "author", 1L, null, 2, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        given(commentService.findParentCommentsByPostId(1L)).willReturn(List.of(comment));

        // expect
        mockMvc.perform(get("/api/v1/posts/1/comments/parents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].replyCount").value(2));
    }

    @Test
    @DisplayName("댓글 페이징 조회에 성공한다")
    void getParentCommentsWithPagination() throws Exception {
        // given
        CommentResponseDto comment = new CommentResponseDto(
                1L, "Comment", "author", 1L, null, 0, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        PageResponse<CommentResponseDto> pageResponse = PageResponse.of(
                List.of(comment), 0, 10, 1, 1
        );

        given(commentService.findParentCommentsWithPagination(1L, 0, 10))
                .willReturn(pageResponse);

        // expect
        mockMvc.perform(get("/api/v1/posts/1/comments/page")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("답글 목록 조회에 성공한다")
    void getReplies() throws Exception {
        // given
        CommentResponseDto reply = new CommentResponseDto(
                2L, "Reply", "author", 1L, 1L, 0, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        given(commentService.findRepliesByParentId(1L)).willReturn(List.of(reply));

        // expect
        mockMvc.perform(get("/api/v1/posts/1/comments/1/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parentId").value(1));
    }

    @Test
    @DisplayName("댓글 수 조회에 성공한다")
    void getCommentCount() throws Exception {
        // given
        given(commentService.countByPostId(1L)).willReturn(5L);

        // expect
        mockMvc.perform(get("/api/v1/posts/1/comments/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }
}

