package com.kraft.config;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostImage;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 숨김·소프트 삭제된 글에 붙은 이미지는 공개 {@code /images/**}로 내려가지 않는다. 판정은 짧게 보관되므로 상태마다 다른 파일을 쓴다.
 * 파일 하나당 대장 행과 실제 파일이 모두 있어야 "막혀서 404"와 "원래 없어서 404"를 구별할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HiddenPostImageTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private TransactionTemplate tx;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final List<Path> files = new ArrayList<>();

    @AfterEach
    void cleanFiles() throws IOException {
        for (Path file : files) {
            Files.deleteIfExists(file);
        }
    }

    private User author() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().name("img-" + suffix).email("img-" + suffix + "@example.com")
                .password("x").role(Role.USER).build());
    }

    /** 대장 행과 실제 파일을 만든다. {@code post}가 null이면 글에 붙지 않은(ORPHAN) 이미지다. */
    private String image(User owner, Post post) throws IOException {
        String fileName = UUID.randomUUID() + ".png";
        Path dir = Path.of(uploadDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, "x");
        files.add(file);
        PostImage image = new PostImage(fileName, owner, 1L, 1, 1);
        if (post != null) {
            image.attachTo(post);
        }
        postImageRepository.save(image);
        return fileName;
    }

    private Post post(User owner) {
        return postRepository.save(Post.builder().title("t").content("c").user(owner).build());
    }

    @Test
    @DisplayName("보이는 글의 이미지와 글에 붙지 않은 이미지는 내려준다")
    void visibleAndOrphanImages_areServed() throws Exception {
        User owner = author();
        String attached = image(owner, post(owner));
        String orphan = image(owner, null);

        mockMvc.perform(get("/images/" + attached)).andExpect(status().isOk());
        mockMvc.perform(get("/images/" + orphan)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("숨긴 글의 이미지는 404다")
    void blindedPostImage_isNotFound() throws Exception {
        User owner = author();
        Post post = post(owner);
        String fileName = image(owner, post);
        tx.executeWithoutResult(status -> postRepository.blind(post.getId(), LocalDateTime.now()));

        mockMvc.perform(get("/images/" + fileName)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("소프트 삭제된 글의 이미지는 404다")
    void deletedPostImage_isNotFound() throws Exception {
        User owner = author();
        Post post = post(owner);
        String fileName = image(owner, post);
        tx.executeWithoutResult(status -> postRepository.softDelete(post.getId(), LocalDateTime.now()));

        mockMvc.perform(get("/images/" + fileName)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("대장에 없는 파일은 막지 않고 정적 처리에 맡긴다(없으면 404, 있으면 200)")
    void unknownFile_isLeftToStaticHandler() throws Exception {
        mockMvc.perform(get("/images/" + UUID.randomUUID() + ".png")).andExpect(status().isNotFound());
        Path file = Path.of(uploadDir).resolve("plain-" + UUID.randomUUID() + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
        files.add(file);

        mockMvc.perform(get("/images/" + file.getFileName())).andExpect(status().isOk());
    }
}
