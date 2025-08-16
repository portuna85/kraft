package com.example.board;

import com.example.board.member.MemberRepository;
import com.example.board.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestConfig.class, MemberService.class})
class MemberServiceTest {

    @Autowired MemberRepository memberRepository;
    @Autowired MemberService memberService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("중복 이메일 가입 방지")
    void duplicate_email_not_allowed() {
        memberService.register("dup@a.com", "alice", "12345678");
        assertThatThrownBy(() -> memberService.register("dup@a.com", "alice2", "87654321"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미 가입된 이메일");
    }
}
