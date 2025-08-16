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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestConfig.class)
class CommentPaginationTest {
    @Autowired MemberRepository memberRepository;
    @Autowired PostService postService;
    @Autowired CommentService commentService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("루트 댓글 페이징")
    void root_comment_pagination() {
        Member a = memberRepository.save(new Member("a@a.com","alice", passwordEncoder.encode("pass1234")));
        Post p = postService.write("t","c", a);
        for (int i=0; i<12; i++) {
            commentService.write(p.getId(), a, "root-" + i);
        }
        Page<Comment> page0 = commentService.listRoot(p.getId(), PageRequest.of(0,5));
        Page<Comment> page1 = commentService.listRoot(p.getId(), PageRequest.of(1,5));
        assertThat(page0.getContent()).hasSize(5);
        assertThat(page1.getContent()).hasSize(5);
        assertThat(page1.getTotalElements()).isEqualTo(12);
        assertThat(page1.getTotalPages()).isEqualTo(3);
    }
}
