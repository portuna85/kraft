package com.kraft.post.service;

import com.kraft.post.domain.PostImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * B02의 최후 수단인 디스크-대장 대조를 실제 파일로 검증한다. 대장 조회는 mock으로 대신해,
 * "어떤 파일이 대장에 있는가"를 테스트가 직접 통제한다.
 */
class OrphanFileReconcilerTest {

    @TempDir
    Path uploadDir;

    private PostImageRepository postImageRepository;
    private OrphanFileReconciler reconciler;

    @BeforeEach
    void setUp() {
        postImageRepository = mock(PostImageRepository.class);
        reconciler = new OrphanFileReconciler(postImageRepository);
        ReflectionTestUtils.setField(reconciler, "uploadDir", uploadDir.toString());
    }

    @Test
    @DisplayName("대장에 없고 유예시간이 지난 파일만 지운다")
    void reconcileNow_removesOnlyOldFilesMissingFromLedger() throws IOException {
        Path orphaned = createFile("orphaned.png", Instant.now().minus(OrphanFileReconciler.GRACE_PERIOD.plusMinutes(1)));
        Path recentlyOrphaned = createFile("recently-orphaned.png", Instant.now());
        Path registered = createFile("registered.png", Instant.now().minus(OrphanFileReconciler.GRACE_PERIOD.plusMinutes(1)));

        // B11: 파일마다 existsByFileName을 따로 묻지 않고 청크 단위로 findFileNamesIn을 한 번
        // 부른다 — 대장에 있는 파일명만 돌려준다(대상에 없는 orphaned.png는 빠진다).
        given(postImageRepository.findFileNamesIn(anyList())).willReturn(List.of("registered.png"));

        int deleted = reconciler.reconcileNow(Instant.now().minus(OrphanFileReconciler.GRACE_PERIOD));

        assertThat(deleted).isEqualTo(1);
        assertThat(Files.exists(orphaned)).isFalse();
        // 방금 저장된 파일은 아직 대장 등록이 진행 중일 수 있으므로 유예시간 안에는 건드리지 않는다.
        assertThat(Files.exists(recentlyOrphaned)).isTrue();
        // 대장에 있는 파일은 나이와 무관하게 보존한다.
        assertThat(Files.exists(registered)).isTrue();
    }

    @Test
    @DisplayName("업로드 디렉터리가 비어 있으면 아무 일도 하지 않는다")
    void reconcileNow_whenDirectoryEmpty_doesNothing() {
        int deleted = reconciler.reconcileNow(Instant.now());

        assertThat(deleted).isZero();
    }

    private Path createFile(String name, Instant lastModified) throws IOException {
        Path file = uploadDir.resolve(name);
        Files.writeString(file, "fake-image-bytes");
        Files.setLastModifiedTime(file, FileTime.from(lastModified));
        return file;
    }
}
