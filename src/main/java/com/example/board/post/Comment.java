package com.example.board.post;

import com.example.board.domain.BaseTimeEntity;
import com.example.board.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comments")
public class Comment extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member author;
    @NotBlank
    @Column(nullable = false, length = 500)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> children = new ArrayList<>();

    @Column(nullable = false)
    private int depth = 0; // 0: 루트, 1: 대댓글

    protected Comment() {
    }

    public Comment(Post post, Member author, String content) {
        this.post = post;
        this.author = author;
        this.content = content;
        this.depth = 0;
    }

    public Comment(Post post, Member author, String content, Comment parent) {
        this.post = post;
        this.author = author;
        this.content = content;
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public Member getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public Comment getParent() {
        return parent;
    }

    public List<Comment> getChildren() {
        return children;
    }

    public int getDepth() {
        return depth;
    }

    public void edit(String content) {
        this.content = content;
    }
}
