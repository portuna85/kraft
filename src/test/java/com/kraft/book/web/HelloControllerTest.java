package com.kraft.book.web;

import com.kraft.book.config.JpaConfig; // ★ JPA 감사 설정 분리해두셨다면 이걸 제외
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = HelloController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JpaConfig.class               // ★ 감사를 웹 슬라이스에서 제외
        )
)
@AutoConfigureMockMvc(addFilters = false)       // ★ Security 의존성이 있으면 401 대비
class HelloControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("/hello 요청 시 'hello' 반환")
    void hello_리턴() throws Exception {
        mvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("hello"));
    }

    @Test
    @DisplayName("/hello/dto 요청 시 JSON 반환")
    void helloDto_리턴() throws Exception {
        String name = "홍길동";
        int amount = 1000;

        mvc.perform(get("/hello/dto")
                        .param("name", name)
                        .param("amount", String.valueOf(amount)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name", is(name)))
                .andExpect(jsonPath("$.amount", is(amount)));
    }
}
