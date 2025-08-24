package com.boardly.security;


import com.boardly.post.web.api.PostApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(PostApiController.class)
class SecurityAccessTest {
    @Autowired MockMvc mockMvc;


    @Test
    void api_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isUnauthorized()); // Security 설정에 따라 302/401 중 선택
    }
}