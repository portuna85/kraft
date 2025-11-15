package com.kraft.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.PostSaveRequestDto;
import com.kraft.web.dto.PostUpdateRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc // 보안 필터 적용
@ActiveProfiles("test")
@Transactional
public class PostApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User other;

    @BeforeEach
    void initUsers() {
        owner = userRepository.save(User.builder()
                .name("Owner")
                .email("owner@example.com")
                .role(Role.USER)
                .build());
        other = userRepository.save(User.builder()
                .name("Other")
                .email("other@example.com")
                .role(Role.USER)
                .build());
    }

    @Test
    void authorizationFlow() throws Exception {
        // 1. 비로그인 상태에서 생성 시도 -> 401 (인증 필요)
        PostSaveRequestDto saveDto = PostSaveRequestDto.builder()
                .title("Flow Title")
                .content("Flow Content")
                .author("someone")
                .build();
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveDto)))
                .andExpect(status().isUnauthorized());

        // 2. owner 인증 후 생성 -> 201
        String createdIdStr = mockMvc.perform(post("/api/posts")
                        .with(user(owner.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveDto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long postId = Long.parseLong(createdIdStr);

        // 3. 소유자(owner) 수정 -> 200
        PostUpdateRequestDto updateOwner = PostUpdateRequestDto.builder()
                .title("Owner Updated")
                .content("Owner Content Updated")
                .build();
        mockMvc.perform(put("/api/posts/" + postId)
                        .with(user(owner.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateOwner)))
                .andExpect(status().isOk());

        // 4. 다른 사용자(other) 수정 시도 -> 403
        PostUpdateRequestDto updateOther = PostUpdateRequestDto.builder()
                .title("Other Updated Attempt")
                .content("Should Fail")
                .build();
        mockMvc.perform(put("/api/posts/" + postId)
                        .with(user(other.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateOther)))
                .andExpect(status().isForbidden());

        // 5. 다른 사용자(other) 삭제 시도 -> 403
        mockMvc.perform(delete("/api/posts/" + postId)
                        .with(user(other.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        // 6. 소유자(owner) 삭제 -> 200
        mockMvc.perform(delete("/api/posts/" + postId)
                        .with(user(owner.getEmail()).roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void ownerProtectionWorks() throws Exception {
        // owner 인증 후 생성 -> 201
        PostSaveRequestDto saveDto = PostSaveRequestDto.builder()
                .title("Owned Title")
                .content("Owned Content")
                .author("owner")
                .build();
        Long ownedId = Long.valueOf(mockMvc.perform(post("/api/posts")
                        .with(user(owner.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveDto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // other 사용자 수정 -> 403
        PostUpdateRequestDto badUpdate = PostUpdateRequestDto.builder()
                .title("Bad Update")
                .content("Not Allowed")
                .build();
        mockMvc.perform(put("/api/posts/" + ownedId)
                        .with(user(other.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badUpdate)))
                .andExpect(status().isForbidden());

        // owner 수정 -> 200
        PostUpdateRequestDto goodUpdate = PostUpdateRequestDto.builder()
                .title("Good Update")
                .content("Owner Allowed")
                .build();
        mockMvc.perform(put("/api/posts/" + ownedId)
                        .with(user(owner.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodUpdate)))
                .andExpect(status().isOk());

        // other 삭제 -> 403
        mockMvc.perform(delete("/api/posts/" + ownedId)
                        .with(user(other.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        // owner 삭제 -> 200
        mockMvc.perform(delete("/api/posts/" + ownedId)
                        .with(user(owner.getEmail()).roles("USER")))
                .andExpect(status().isOk());
    }
}
