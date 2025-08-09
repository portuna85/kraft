package com.kraft.book.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.book.domain.posts.Posts;
import com.kraft.book.domain.posts.PostsRepository;
import com.kraft.book.web.dto.PostsSaveRequestDto;
import com.kraft.book.web.dto.PostsUpdateRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PostsApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostsRepository postsRepository;

    @AfterEach
    void tearDown() { postsRepository.deleteAll(); }

    @Test
    @WithMockUser(roles = "USER")
    void Posts_등록된다() throws Exception {
        PostsSaveRequestDto dto = PostsSaveRequestDto.builder()
                .title("제목").content("내용").author("author").build();

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(csrf()))                        // ★ 중요
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void Posts_수정된다() throws Exception {
        Posts saved = postsRepository.save(Posts.builder()
                .title("타이틀").content("내용").author("author").build());

        PostsUpdateRequestDto dto = PostsUpdateRequestDto.builder()
                .title("타이틀 수정").content("내용 수정").build();

        mockMvc.perform(put("/api/v1/posts/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(csrf()))                        // ★ 중요
                .andExpect(status().isOk());
    }
}
