package com.kraft.post.service;

import com.kraft.post.domain.PostImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
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

        List<Path> candidates;
        try (Stream<Path> files = Files.list(dir)) {
            candidates = files.filter(Files::isRegularFile)
                    .filter(path -> isOlderThan(path, threshold))
                    .toList();
        } catch (IOException e) {
            log.warn("업로드 디렉터리를 읽지 못해 이번 주기의 대조를 건너뜁니다.", e);
            return 0;
        }

        int deleted = 0;
        for (int start = 0; start < candidates.size(); start += EXISTS_CHECK_CHUNK_SIZE) {
            List<Path> chunk = candidates.subList(start, Math.min(start + EXISTS_CHECK_CHUNK_SIZE, candidates.size()));
            List<String> chunkFileNames = chunk.stream().map(path -> path.getFileName().toString()).toList();
            // 청크 전체를 한 번에 물어 대장에 있는 파일명 집합을 구한다(B11) — 파일마다 따로
            // existsByFileName을 부르지 않는다.
            Set<String> knownFileNames = new HashSet<>(postImageRepository.findFileNamesIn(chunkFileNames));
            for (Path path : chunk) {
                if (!knownFileNames.contains(path.getFileName().toString()) && deleteQuietly(path)) {
                    deleted++;
                }
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
