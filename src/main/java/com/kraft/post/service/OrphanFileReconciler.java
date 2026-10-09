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
 * 업로드 디렉터리의 파일과 {@code post_images} 대장을 대조해 대장에 없는 파일만 지우는 최후 수단. {@code PostService.uploadImage}의
 * 보상 삭제와 {@code OnRollback}이 놓치는 경우(커밋 직후 프로세스 종료 등)를 디스크를 직접 훑어 잡는다. 업로드 진행 중인
 * 파일을 지우지 않도록 유예시간이 지난 파일만 대상으로 한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrphanFileReconciler {

    static final Duration GRACE_PERIOD = Duration.ofHours(1);

    /** 대장 존재 여부를 한 번에 묻는 청크 크기. 파일마다 따로 조회하지 않는다. */
    private static final int EXISTS_CHECK_CHUNK_SIZE = 500;

    private final PostImageRepository postImageRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.reconcile-enabled:true}")
    private boolean enabled;

    /**
     * 한 주기가 볼 파일 수의 상한. 후보를 지연 반복자로 훑으며 500개씩 처리하고, 상한에 닿으면 남은 파일은 다음 주기로
     * 미룬다({@link #skipOffset}). 필드 기본값도 둔다 — Spring 없이 생성자로 만드는 테스트에서는 {@code @Value}가
     * 주입되지 않아 0이 되면 하나도 못 본다.
     */
    @Value("${app.upload.reconcile-max-files-per-run:50000}")
    private int maxFilesPerRun = 50_000;

    /** 지난 주기가 상한에 걸려 끝까지 못 봤으면 이번에는 그 뒤부터 이어 본다. 재시작하면 0으로 돌아가지만 최후 수단 작업에는 괜찮다. */
    private volatile long skipOffset = 0;

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
        boolean reachedEnd = true;
        List<Path> chunk = new ArrayList<>(EXISTS_CHECK_CHUNK_SIZE);
        try (Stream<Path> files = Files.list(dir)) {
            Iterator<Path> candidates = files.filter(Files::isRegularFile)
                    .filter(path -> isOlderThan(path, threshold))
                    .skip(skipOffset)
                    .iterator();
            while (scanned < maxFilesPerRun && candidates.hasNext()) {
                chunk.add(candidates.next());
                scanned++;
                if (chunk.size() == EXISTS_CHECK_CHUNK_SIZE) {
                    deleted += deleteUnknown(chunk);
                    chunk.clear();
                }
            }
            reachedEnd = !candidates.hasNext();
        } catch (IOException | UncheckedIOException e) {
            log.warn("업로드 디렉터리를 읽지 못해 이번 주기의 대조를 중단합니다. scanned={}, deleted={}", scanned, deleted, e);
            deleted += deleteUnknown(chunk);
            return deleted;
        }
        deleted += deleteUnknown(chunk);

        // 상한에 걸려 끝까지 못 봤으면 다음 주기는 이어서 본다(앞쪽이 전부 정상 파일이면 뒤의 고아에 영원히 못 닿는다).
        // 끝까지 봤으면 처음부터 다시 돈다(앞쪽도 주기적으로 대조해야 한다).
        skipOffset = reachedEnd ? 0 : skipOffset + scanned;

        log.info("업로드 디렉터리 대조를 마쳤습니다. scanned={}, deleted={}, maxFilesPerRun={}, nextSkipOffset={}",
                scanned, deleted, maxFilesPerRun, skipOffset);
        return deleted;
    }

    /** 청크 전체를 한 번에 물어 대장에 있는 파일명 집합을 구한다 — 파일마다 따로 existsByFileName을 부르지 않는다. */
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
