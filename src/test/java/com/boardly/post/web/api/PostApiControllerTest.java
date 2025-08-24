package com.boardly.post.web.api;

import com.boardly.post.service.PostService;
import com.boardly.user.domain.Role;
import com.boardly.user.domain.User;
import com.boardly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // ✅ 핵심: 새 import
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostApiController.class)
class PostApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostService postService;   // ✅ 기존 @MockBean -> @MockitoBean

    @MockitoBean
    UserService userService;   // ✅ 기존 @MockBean -> @MockitoBean

    @Test
    @DisplayName("POST /api/posts 는 인증 후 200을 반환한다")
    @WithMockUser(username = "u1", roles = {"USER"})
    void create() throws Exception {
        Mockito.when(userService.findByUsername("u1")).thenReturn(
                User.builder()
                        .username("u1")
                        .password("pw")
                        .nickname("n")
                        .email("e@e.com")
                        .role(Role.USER)
                        .build()
        );

        Mockito.when(postService.create(Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(1L);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk());
    }
}
