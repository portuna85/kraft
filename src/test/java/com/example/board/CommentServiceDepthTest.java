package com.example.board;

import com.example.board.member.Member;
import com.example.board.member.MemberRepository;
import com.example.board.post.Comment;
import com.example.board.post.CommentService;
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
class CommentServiceDepthTest {

    @Autowired MemberRepository memberRepository;
    @Autowired PostService postService;
    @Autowired CommentService commentService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("대댓글은 2단계까지만 허용")
    void reply_depth_limit() {
        Member a = memberRepository.save(new Member("a@a.com","alice", passwordEncoder.encode("pass1234")));
        Post p = postService.write("t","c", a);
        Comment r = commentService.write(p.getId(), a, "root");
        Comment c1 = commentService.reply(p.getId(), r.getId(), a, "child");
        assertThat(c1.getDepth()).isEqualTo(1);
        assertThatThrownBy(() -> commentService.reply(p.getId(), c1.getId(), a, "too deep"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("2단계");
    }
}
