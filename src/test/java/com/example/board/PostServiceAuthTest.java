package com.example.board;

import com.example.board.member.Member;
import com.example.board.member.MemberRepository;
import com.example.board.post.Post;
import com.example.board.post.PostService;
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
@Import(TestConfig.class)
class PostServiceAuthTest {

    @Autowired MemberRepository memberRepository;
    @Autowired PostService postService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("비작성자는 게시글 수정/삭제가 불가해야 한다")
    void only_author_can_edit_or_delete() {
        Member author = memberRepository.save(new Member("a@a.com", "alice", passwordEncoder.encode("12345678")));
        Member bob    = memberRepository.save(new Member("b@b.com", "bob",   passwordEncoder.encode("12345678")));
        Post p = postService.write("hello", "world", author);

        assertThatThrownBy(() -> postService.edit(p.getId(), "x","y", bob))
            .isInstanceOf(SecurityException.class);

        assertThatThrownBy(() -> postService.delete(p.getId(), bob))
            .isInstanceOf(SecurityException.class);
    }
}
