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

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
public class PostCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser = User.builder()
                .name("Integration Tester")
                .email("itest@example.com")
                .picture(null)
                .role(Role.USER)
                .build();
        userRepository.save(testUser);
    }

    @Test
    public void fullCrudFlow_shouldSucceed() throws Exception {
        // Create
        PostSaveRequestDto saveDto = PostSaveRequestDto.builder()
                .title("Integration Title")
                .content("Integration Content")
                .author("tester")
                .build();

        String saveJson = objectMapper.writeValueAsString(saveDto);

        mockMvc.perform(post("/api/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveJson))
                .andExpect(status().isCreated());

        // List - should contain created post
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        // Update - assume id 1 exists in clean test DB
        PostUpdateRequestDto updateDto = PostUpdateRequestDto.builder()
                .title("Updated Title")
                .content("Updated Content")
                .build();

        String updateJson = objectMapper.writeValueAsString(updateDto);

        mockMvc.perform(put("/api/posts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk());

        // Delete
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isOk());
    }
}
