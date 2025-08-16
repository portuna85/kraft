package com.example.board.post;

import com.example.board.domain.BaseTimeEntity;
import com.example.board.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
public class Post extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank
    @Column(nullable = false, length = 150)
    private String title;
    @Lob
    @Column(nullable = false)
    private String content;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member author;
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    protected Post() {
    }

    public Post(String title, String content, Member author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Member getAuthor() {
        return author;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void edit(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
