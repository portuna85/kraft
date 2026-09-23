package com.kraft.post.service;

import com.kraft.post.domain.PostImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * B02의 최후 수단. 업로드 디렉터리의 파일과 {@code post_images} 대장을 대조해, 대장에 없는
 * 파일만 지운다.
 * <p>
 * {@code PostService.uploadImage}는 파일을 저장한 뒤 대장에 등록하고, 등록이 메서드 안에서
 * 실패하면 곧바로 보상 삭제하며, 반환 이후 바깥 트랜잭션의 최종 커밋이 실패하면
 * {@code OnRollback}이 보상 삭제를 시도한다. 그래도 커밋 직후 프로세스가 종료되는 등 두
 * 장치 모두 놓치는 경우가 있을 수 있어, 이 주기 작업이 디스크를 직접 훑어 마지막으로 대조한다.
 * <p>
 * 업로드가 진행 중인 파일을 지우지 않도록, 생성된 지 유예시간이 지난 파일만 대상으로 한다 —
 * 파일 저장과 대장 등록 사이의 정상적인 짧은 간극까지 정리 대상으로 삼으면 안 된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrphanFileReconciler {

    static final Duration GRACE_PERIOD = Duration.ofHours(1);

    /** 대장 존재 여부를 한 번에 묻는 청크 크기(B11). 파일마다 따로 조회하지 않는다. */
    private static final int EXISTS_CHECK_CHUNK_SIZE = 500;

    private final PostImageRepository postImageRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.reconcile-enabled:true}")
    private boolean enabled;

    /**
     * 한 주기가 볼 파일 수의 상한(개선 보고서 PERF-06). 예전에는 유예시간을 지난 후보 전체를
     * {@code Stream.toList()}로 한 번에 메모리에 모은 뒤 청크로 나눠 처리했다 — 디렉터리에
     * 파일이 아주 많이 쌓이면(예: 정리가 한동안 막혀 있던 경우) 그 목록 자체가 heap을 크게
     * 잡아먹는다. 이제는 스트림을 지연 반복자(iterator)로 훑으며 500개씩 처리하고, 이 상한에
     * 닿으면 남은 파일은 다음 주기로 미룬다.
     */
    // 필드 기본값도 함께 둔다 — 테스트는 OrphanFileReconcilerTest처럼 Spring 없이
    // 생성자로 직접 만들어 @Value가 주입되지 않는다. 기본값이 없으면 int 기본값 0이 남아
    // "이번 주기에 하나도 못 본다"는 뜻이 되어 기존 테스트가 전부 깨진다.
    @Value("${app.upload.reconcile-max-files-per-run:50000}")
    private int maxFilesPerRun = 50_000;

    @Scheduled(initialDelayString = "${app.upload.reconcile-initial-delay-ms:1800000}",
            fixedDelayString = "${app.upload.reconcile-interval-ms:3600000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        reconcileNow(Instant.now().minus(GRACE_PERIOD));
    }

    /** 유예시간 기준을 밖에서 주입할 수 있게 나눈 실제 로직. 테스트가 시각을 직접 제어한다. */
    int reconcileNow(Instant threshold) {
        Path dir = Path.of(uploadDir);
        if (!Files.isDirectory(dir)) {
            return 0;
        }

        int scanned = 0;
        int deleted = 0;
        List<Path> chunk = new ArrayList<>(EXISTS_CHECK_CHUNK_SIZE);
        try (Stream<Path> files = Files.list(dir)) {
            Iterator<Path> candidates = files.filter(Files::isRegularFile)
                    .filter(path -> isOlderThan(path, threshold))
                    .iterator();
            while (scanned < maxFilesPerRun && candidates.hasNext()) {
                chunk.add(candidates.next());
                scanned++;
                if (chunk.size() == EXISTS_CHECK_CHUNK_SIZE) {
                    deleted += deleteUnknown(chunk);
                    chunk.clear();
                }
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("업로드 디렉터리를 읽지 못해 이번 주기의 대조를 중단합니다. scanned={}, deleted={}", scanned, deleted, e);
            deleted += deleteUnknown(chunk);
            return deleted;
        }
        deleted += deleteUnknown(chunk);

        log.info("업로드 디렉터리 대조를 마쳤습니다. scanned={}, deleted={}, maxFilesPerRun={}",
                scanned, deleted, maxFilesPerRun);
        return deleted;
    }

    /** 청크 전체를 한 번에 물어 대장에 있는 파일명 집합을 구한다(B11) — 파일마다 따로 existsByFileName을 부르지 않는다. */
    private int deleteUnknown(List<Path> chunk) {
        if (chunk.isEmpty()) {
            return 0;
        }
        List<String> chunkFileNames = chunk.stream().map(path -> path.getFileName().toString()).toList();
        Set<String> knownFileNames = new HashSet<>(postImageRepository.findFileNamesIn(chunkFileNames));
        int deleted = 0;
        for (Path path : chunk) {
            if (!knownFileNames.contains(path.getFileName().toString()) && deleteQuietly(path)) {
                deleted++;
            }
        }
        return deleted;
    }

    private boolean isOlderThan(Path path, Instant threshold) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean deleteQuietly(Path path) {
        try {
            Files.delete(path);
            log.info("대장에 없는 업로드 파일을 정리했습니다. fileName={}", path.getFileName());
            return true;
        } catch (IOException e) {
            log.warn("대장 없는 파일 삭제에 실패했습니다. 다음 주기에 다시 시도합니다. fileName={}", path.getFileName(), e);
            return false;
        }
    }
}
