package com.example.board.file;

import com.example.board.domain.BaseTimeEntity;
import com.example.board.post.Post;
import jakarta.persistence.*;

@Entity
@Table(name = "attachments")
public class Attachment extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Post post;
    @Column(nullable = false) private String originalName;
    @Column(nullable = false) private String storedName;
    @Column(nullable = false) private String url; // /uploads/...
    private String thumbnailUrl; // /uploads/...
    private String contentType;
    private long size;

    protected Attachment() {}
    public Attachment(Post post, String originalName, String storedName, String url, String thumbnailUrl, String contentType, long size) {
        this.post = post; this.originalName = originalName; this.storedName = storedName;
        this.url = url; this.thumbnailUrl = thumbnailUrl; this.contentType = contentType; this.size = size;
    }
    public Long getId() { return id; }
    public Post getPost() { return post; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getUrl() { return url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
}
