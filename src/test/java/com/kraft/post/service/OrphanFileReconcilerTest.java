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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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

    /**
     * PERF-06: 청크 크기(500)를 넘는 후보도 한 번의 주기에서 전부 처리한다 — 예전에도
     * 여러 청크로 나눠 처리하긴 했지만, 그 청크 목록 자체를 만들기 전에 후보 전체를
     * {@code Stream.toList()}로 한 번에 메모리에 모았다. 이번엔 지연 반복자로 스트림을
     * 훑으므로, 청크 경계(500)를 넘나드는 파일 수에서도 정확히 다 지워지고 findFileNamesIn이
     * 청크 수만큼(3번) 불려야 한다.
     */
    @Test
    @DisplayName("PERF-06: 청크 경계(500)를 넘는 후보도 지연 스트림으로 전부 대조한다")
    void reconcileNow_whenCandidatesCrossChunkBoundary_processesAllOfThem() throws IOException {
        int total = 1201;
        Instant old = Instant.now().minus(OrphanFileReconciler.GRACE_PERIOD.plusMinutes(1));
        Set<String> orphanNames = new HashSet<>();
        for (int i = 0; i < total; i++) {
            String name = "orphan-" + i + ".png";
            createFile(name, old);
            orphanNames.add(name);
        }

        // 대장에는 아무것도 없다 — 후보 전체가 orphan이다.
        given(postImageRepository.findFileNamesIn(anyList())).willReturn(List.of());

        int deleted = reconciler.reconcileNow(Instant.now().minus(OrphanFileReconciler.GRACE_PERIOD));

        assertThat(deleted).isEqualTo(total);
        orphanNames.forEach(name -> assertThat(Files.exists(uploadDir.resolve(name))).isFalse());
        // 500, 500, 201 세 청크로 나뉜다.
        verify(postImageRepository, times(3)).findFileNamesIn(anyList());
    }

    /**
     * PERF-06: 한 주기가 볼 파일 수를 {@code maxFilesPerRun}으로 제한한다 — 정리가 한동안
     * 막혀 후보가 아주 많이 쌓여도, 이번 주기가 상한을 넘는 나머지는 건드리지 않고 다음
     * 주기로 미룬다.
     */
    @Test
    @DisplayName("PERF-06: maxFilesPerRun을 넘는 후보는 이번 주기에서 건드리지 않는다")
    void reconcileNow_boundsWorkByMaxFilesPerRun() throws IOException {
        Instant old = Instant.now().minus(OrphanFileReconciler.GRACE_PERIOD.plusMinutes(1));
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            files.add(createFile("orphan-" + i + ".png", old));
        }
        ReflectionTestUtils.setField(reconciler, "maxFilesPerRun", 3);
        given(postImageRepository.findFileNamesIn(anyList())).willReturn(List.of());

        int deleted = reconciler.reconcileNow(Instant.now().minus(OrphanFileReconciler.GRACE_PERIOD));

        assertThat(deleted).isEqualTo(3);
        long remaining = files.stream().filter(Files::exists).count();
        assertThat(remaining).isEqualTo(7);
    }

    private Path createFile(String name, Instant lastModified) throws IOException {
        Path file = uploadDir.resolve(name);
        Files.writeString(file, "fake-image-bytes");
        Files.setLastModifiedTime(file, FileTime.from(lastModified));
        return file;
    }
}
