package com.kraft.backup;

import com.kraft.KraftApplication;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.service.PostImageService;
import com.kraft.post.service.PostService;
import com.kraft.support.TestImages;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * README "백업·복구" 절차를 실제로 밟아 본다 (개선 보고서 "백업·복구" 공백).
 *
 * <h3>왜 필요한가</h3>
 * 백업 명령은 문서에 있었지만 <b>그것으로 정말 복구되는지는 아무도 확인하지 않았다.</b>
 * 백업이 쓸모없다는 사실을 장애 당일에 알게 되는 것이 가장 나쁜 결과다.
 * <p>
 * README는 "세 가지를 같은 시점으로 함께" 보관하라고 말한다 — DB 덤프, {@code uploads/images/},
 * 그리고 {@code EMAIL_ENCRYPTION_KEY}. 여기서는 그 주장을 <b>실행되는 형태</b>로 바꾼다.
 * 셋을 모두 갖추면 복구되고, <b>하나라도 빠지면 어떻게 망가지는지</b>를 함께 고정한다.
 * 후자가 없으면 "왜 세 개나 챙겨야 하는가"가 설득되지 않는다.
 */
@Testcontainers(disabledWithoutDocker = true)
class BackupRestoreRehearsalTest {

    @Container
    static MariaDBContainer mariadb = new MariaDBContainer("mariadb:11.7.2");

    private static final String KEY = "backup-drill-key-1111";

    /** 복구가 끝난 뒤 되살아났는지 확인할 것들. */
    private record Seeded(String email, Long postId, String pictureUrl) {
    }

    @Test
    @DisplayName("덤프·업로드·키를 함께 복구하면 로그인도 글도 이미지도 되살아난다")
    void restoringAllThreePartsBringsBackAWorkingSystem() {
        Path uploads = tempDir("uploads");
        Seeded seeded = seed(uploads);

        Path dump = backupDatabase();
        Path uploadsBackup = copyDirectory(uploads, tempDir("uploads-backup"));

        destroyEverything(uploads);

        restoreDatabase(dump);
        copyDirectory(uploadsBackup, uploads);

        // 스키마까지 온전히 돌아왔다면 validate로 기동된다. 덤프에 빠진 테이블이 있으면 여기서 깨진다.
        withApp(uploads, context -> {
            UserRepository users = context.getBean(UserRepository.class);
            User restored = users.findByEmailHash(EmailHasher.sha512Hex(seeded.email())).orElseThrow();

            assertThat(restored.getEmail())
                    .as("같은 키로 기동했으므로 저장된 이메일이 그대로 읽혀야 한다")
                    .isEqualTo(seeded.email());

            var post = context.getBean(PostRepository.class).findById(seeded.postId()).orElseThrow();
            assertThat(post.getPicture()).isEqualTo(seeded.pictureUrl());

            assertThat(uploads.resolve(PostImageService.fileNameOf(seeded.pictureUrl())))
                    .as("DB가 가리키는 파일이 실제로 있어야 글이 깨져 보이지 않는다")
                    .exists();
        }, KEY);
    }

    /**
     * README가 키를 백업 대상에 넣은 이유를 고정한다.
     * <p>
     * 키를 잃으면 <b>DB 복구 자체는 성공한다</b> — SQL로 보면 행이 그대로 있다. 그런데 앱은
     * 그 회원을 <b>읽어 올 수조차 없다.</b> 복호화가 엔티티를 만드는 시점에 일어나므로
     * 조회가 통째로 실패한다. 덤프만 잘 보관해도 소용이 없다는 뜻이다.
     */
    @Test
    @DisplayName("키를 빠뜨리면 DB를 되돌려도 저장된 이메일을 읽을 수 없다")
    void restoringWithoutTheEncryptionKeyLeavesEmailsUnreadable() {
        Path uploads = tempDir("uploads-nokey");
        Seeded seeded = seed(uploads);

        Path dump = backupDatabase();
        destroyEverything(uploads);
        restoreDatabase(dump);

        // 행은 SQL로 보면 멀쩡히 살아 있다. 덤프는 제 몫을 다한 것이다.
        Long rows = jdbc().queryForObject("SELECT COUNT(*) FROM users WHERE email_hash = ?",
                Long.class, EmailHasher.sha512Hex(seeded.email()));
        assertThat(rows).as("DB 복구 자체는 성공했다").isEqualTo(1L);

        withApp(uploads, context -> {
            UserRepository users = context.getBean(UserRepository.class);

            // 그런데 앱은 이 회원을 읽어 올 수조차 없다. 복호화는 엔티티를 만드는 시점에
            // 일어나므로, 조회가 통째로 실패한다.
            assertThatThrownBy(() -> users.findByEmailHash(EmailHasher.sha512Hex(seeded.email())))
                    .as("키가 없으면 행이 남아 있어도 쓸 수 없다 — 그래서 키도 함께 백업한다")
                    .hasRootCauseInstanceOf(javax.crypto.AEADBadTagException.class);
        }, "completely-different-key-2222");
    }

    /**
     * README의 "DB만 되돌리면 {@code post_images} 행은 있는데 파일이 없는 상태가 될 수 있습니다"를
     * 그대로 확인한다. 이 상태는 앱이 오류를 내지 않아 더 위험하다 — 글은 열리고 이미지만 깨진다.
     */
    @Test
    @DisplayName("업로드 파일을 빠뜨리면 DB에는 이미지가 있는데 파일이 없다")
    void restoringWithoutTheUploadedFilesLeavesDanglingImageRows() {
        Path uploads = tempDir("uploads-nofiles");
        Seeded seeded = seed(uploads);

        Path dump = backupDatabase();
        destroyEverything(uploads);
        restoreDatabase(dump);
        // uploads는 일부러 복구하지 않는다.

        withApp(uploads, context -> {
            var post = context.getBean(PostRepository.class).findById(seeded.postId()).orElseThrow();
            assertThat(post.getPicture()).isEqualTo(seeded.pictureUrl());
            assertThat(uploads.resolve(PostImageService.fileNameOf(seeded.pictureUrl())))
                    .as("DB는 이 파일을 가리키는데 디스크에는 없다")
                    .doesNotExist();
        }, KEY);
    }

    // --- 절차 ---------------------------------------------------------------

    /** 회원·게시글·업로드 이미지를 하나씩 만든다. 복구 후 되살아났는지 볼 대상이다. */
    private Seeded seed(Path uploads) {
        dropAllTables();

        String email = "backup-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        var seeded = new Object() {
            Long postId;
            String pictureUrl;
        };

        withApp(uploads, context -> {
            User author = context.getBean(UserRepository.class).save(User.builder()
                    .name("backup-" + UUID.randomUUID().toString().substring(0, 8))
                    .email(email)
                    .password("encoded")
                    .role(Role.USER)
                    .build());
            Authentication auth = new UsernamePasswordAuthenticationToken(author.getEmail(), null,
                    List.of(new SimpleGrantedAuthority(Role.USER.getKey())));

            PostService posts = context.getBean(PostService.class);
            seeded.pictureUrl = posts.uploadImage(TestImages.pngFile("drill.png"), auth);
            seeded.postId = posts.save(auth,
                    new PostSaveRequestDto("백업 리허설", "복구 후에도 남아 있어야 한다",
                            seeded.pictureUrl, Category.FREE));
        }, KEY, "--spring.jpa.hibernate.ddl-auto=update");

        return new Seeded(email, seeded.postId, seeded.pictureUrl);
    }

    /**
     * deploy/backup.sh의 덤프 명령과 같은 모양(gzip 압축, OPS-G3/OPS-C4)으로 맞춘다.
     * <p>
     * 완전히 같지는 않다 — 운영은 {@code docker compose exec} 컨테이너 안에서 root 계정을
     * {@code MYSQL_PWD}로 쓰는데, 이 테스트는 Testcontainers가 만든 앱 계정(-p 인자)을 그대로
     * 쓴다(OPS-C4, [확인 필요]) — {@code MariaDBContainer}가 root 자격 증명을 별도로 노출하지
     * 않아, 트리거 {@code DEFINER}·권한 차이까지 이 테스트가 검증하지는 못한다.
     */
    private Path backupDatabase() {
        exec("mariadb-dump -u" + mariadb.getUsername() + " -p" + mariadb.getPassword()
                + " --single-transaction " + mariadb.getDatabaseName() + " | gzip > /tmp/backup.sql.gz");

        Path dumpGz = tempDir("dump").resolve("backup.sql.gz");
        mariadb.copyFileFromContainer("/tmp/backup.sql.gz", dumpGz.toString());
        assertThat(dumpGz).as("덤프가 비어 있으면 백업이 아니라 빈 파일을 보관해 온 것이다").isNotEmptyFile();

        Path dump = tempDir("dump").resolve("backup.sql");
        gunzip(dumpGz, dump);
        return dump;
    }

    private void gunzip(Path source, Path target) {
        try (var in = new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(source))) {
            java.nio.file.Files.copy(in, target);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** 장애 상황을 만든다 — DB도 업로드 파일도 사라진다. */
    private void destroyEverything(Path uploads) {
        dropAllTables();
        deleteRecursively(uploads);
        createDirectory(uploads);
    }

    private void restoreDatabase(Path dump) {
        mariadb.copyFileToContainer(MountableFile.forHostPath(dump), "/tmp/restore.sql");
        exec("mariadb -u" + mariadb.getUsername() + " -p" + mariadb.getPassword()
                + " " + mariadb.getDatabaseName() + " < /tmp/restore.sql");
    }

    // --- 도구 ---------------------------------------------------------------

    private void exec(String shellCommand) {
        try {
            var result = mariadb.execInContainer("sh", "-c", shellCommand);
            assertThat(result.getExitCode())
                    .as("컨테이너 명령 실패: %s%n%s", shellCommand, result.getStderr())
                    .isZero();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * 앱을 띄워 작업을 시키고 닫는다. 기본은 운영과 같은 {@code validate}다 — 복구된 스키마가
     * 엔티티와 맞는지까지 함께 확인된다.
     */
    private void withApp(Path uploads, java.util.function.Consumer<ConfigurableApplicationContext> work,
                         String encryptionKey, String... extraArgs) {
        String[] args = new String[extraArgs.length + 8];
        // build.gradle.kts가 모든 Test 태스크에 걸어 두는 test 프로파일(H2)을 덮어써야 한다.
        args[0] = "--spring.profiles.active=backup-drill";
        args[1] = "--spring.datasource.url=" + mariadb.getJdbcUrl();
        args[2] = "--spring.datasource.username=" + mariadb.getUsername();
        args[3] = "--spring.datasource.password=" + mariadb.getPassword();
        args[4] = "--server.port=0";
        args[5] = "--app.security.email-encryption-key=" + encryptionKey;
        args[6] = "--app.upload.dir=" + uploads;
        args[7] = "--spring.flyway.enabled=false";
        System.arraycopy(extraArgs, 0, args, 8, extraArgs.length);

        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(KraftApplication.class).run(args)) {
            work.accept(context);
        }
    }

    private void dropAllTables() {
        JdbcTemplate jdbc = jdbc();
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()",
                String.class);

        // FOREIGN_KEY_CHECKS는 세션 변수다. DriverManagerDataSource는 호출마다 새 커넥션을
        // 열기 때문에 한 커넥션 안에서 끝내야 한다.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    statement.execute("DROP TABLE IF EXISTS `" + table + "`");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword());
        source.setDriverClassName(mariadb.getDriverClassName());
        return new JdbcTemplate(source);
    }

    private static Path tempDir(String prefix) {
        try {
            Path dir = Files.createTempDirectory("kraft-" + prefix);
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path copyDirectory(Path source, Path target) {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
