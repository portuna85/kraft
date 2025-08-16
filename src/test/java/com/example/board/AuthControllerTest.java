package com.example.board;

import com.example.board.member.MemberService;
import com.example.board.web.AuthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean MemberService memberService;

    @Test
    @DisplayName("회원가입 성공시 로그인 페이지로 리다이렉트")
    void signup_success() throws Exception {
        doReturn(null).when(memberService).register(anyString(), anyString(), anyString());
        mvc.perform(post("/signup")
                .param("email", "a@a.com")
                .param("username", "alice")
                .param("password", "12345678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }
}
