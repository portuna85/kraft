package com.kraft.e2e;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.service.ImportedDraw;
import com.kraft.recommend.service.RecommendationHistoryImporter;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * E2E가 기대는 고정 데이터를 만든다. H2 인메모리 + {@code create-drop}이므로 매 기동마다
 * 같은 상태에서 시작한다.
 * <p>
 * 이 시더가 필요한 이유는 <b>이메일 인증 때문</b>이다. 회원가입으로 만든 계정은 GUEST라
 * 글을 쓸 수 없고, USER로 올리려면 메일로 받은 링크를 눌러야 한다. 대부분의 시나리오는
 * "이미 인증을 마친 사용자"에서 시작해야 하므로 여기서 직접 만든다.
 * <p>
 * {@code @Profile("e2e")}라 운영·로컬에서는 빈 자체가 만들어지지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Profile("e2e")
@Component
public class E2eDataInitializer implements ApplicationRunner {

    /**
     * 시드 계정의 단일 출처. Playwright 쪽 e2e/fixtures.js도 같은 파일을 읽는다 — 이메일·이름·비밀번호를
     * JS와 Java에 따로 적어 두면 한쪽만 바뀌어 로그인이 조용히 어긋난다. 비밀번호는 정책(8자 이상,
     * 대·소문자·특수문자)을 만족해야 한다.
     * <p>
     * pwchange는 비밀번호 변경 시나리오 전용이다. 비밀번호를 실제로 바꾸고 모든 세션을 끊는 테스트가
     * admin을 쓰면, 변경과 복원 사이에서 실패했을 때 같은 샤드의 나머지 테스트가 줄줄이 로그인에 실패했다.
     */
    private static final String ACCOUNTS_RESOURCE = "e2e-accounts.json";

    private record SeedAccount(String email, String name, Role role) {
    }

    private record SeedAccounts(String password, Map<String, SeedAccount> accounts) {
    }

    private static SeedAccounts loadSeedAccounts() {
        try (InputStream in = E2eDataInitializer.class.getClassLoader().getResourceAsStream(ACCOUNTS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(ACCOUNTS_RESOURCE + "를 찾을 수 없습니다.");
            }
            return new ObjectMapper().readValue(in, SeedAccounts.class);
        } catch (IOException e) {
            throw new IllegalStateException(ACCOUNTS_RESOURCE + "를 읽지 못했습니다.", e);
        }
    }

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecommendationHistoryStateRepository historyStateRepository;
    private final RecommendationHistoryImporter historyImporter;

    /** 시드 당첨 이력의 회차 수. 1..N이 비는 곳 없이 이어져야 추천 이력이 "준비됨"이 된다. */
    static final int SEEDED_ROUNDS = 30;

    /** 시드 공지의 고정 기한. 테스트가 도는 동안 풀리지 않도록 아주 먼 미래로 둔다(V42와 같은 값). */
    private static final LocalDateTime PINNED_UNTIL = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SeedAccounts seed = loadSeedAccounts();
        User admin = saveUser(seed, "admin");
        User user = saveUser(seed, "user");
        User other = saveUser(seed, "other");
        saveUser(seed, "guest");
        saveUser(seed, "pwchange");

        // 목록 상단 고정은 pinned_until이 있는 글만 된다(V42). 운영은 마이그레이션이 최신 공지를 고정해 두지만
        // 이 프로파일은 Flyway 없이 스키마를 만들므로 시드가 직접 고정해 둔다.
        Post notice = savePost(admin, "공지 게시글", "관리자가 쓴 공지입니다.", Category.NOTICE, PINNED_UNTIL);
        Post mine = savePost(user, "테스터의 글", "테스터가 쓴 자유 게시글입니다.", Category.FREE, null);
        savePost(other, "다른 사람의 글", "소유권 검사를 확인하는 글입니다.", Category.QNA, null);

        commentRepository.save(Comment.builder().content("첫 댓글입니다.").post(mine).user(user).build());
        commentRepository.save(Comment.builder().content("다른 사람의 댓글입니다.").post(notice).user(other).build());

        seedWinningHistory();

        log.info("[E2E] 시드 데이터를 만들었습니다. users={}, posts={}",
                userRepository.count(), postRepository.count());
    }

    /**
     * 당첨 이력 1..{@value #SEEDED_ROUNDS}회를 실제 수입 경로(RecommendationHistoryImporter)로 넣는다.
     * 이 프로파일은 Flyway 없이 엔티티로 스키마를 만들어 V20이 심는 상태 행이 없으므로 먼저 만든다. 이력이
     * 없으면 /recommend가 항상 503이라 성공 경로를 라우트 가로채기로만 검증할 수 있었다.
     * 번호는 회차마다 결정적으로 달라지되 항상 서로 다른 6개(1~42)다.
     */
    private void seedWinningHistory() {
        historyStateRepository.save(RecommendationHistoryState.builder().id(1).version(0L).verifiedThroughRound(0).build());
        List<ImportedDraw> draws = IntStream.rangeClosed(1, SEEDED_ROUNDS)
                .mapToObj(round -> {
                    int base = round % 7;
                    return new ImportedDraw(round, List.of(1 + base, 8 + base, 15 + base, 22 + base, 29 + base, 36 + base));
                })
                .toList();
        historyImporter.importHistory(draws, SEEDED_ROUNDS, "e2e-seed");
    }

    private User saveUser(SeedAccounts seed, String key) {
        SeedAccount account = seed.accounts().get(key);
        return userRepository.save(User.builder()
                .name(account.name())
                .email(account.email())
                .password(passwordEncoder.encode(seed.password()))
                .role(account.role())
                .build());
    }

    private Post savePost(User author, String title, String content, Category category, LocalDateTime pinnedUntil) {
        return postRepository.save(Post.builder()
                .title(title)
                .content(content)
                .user(author)
                .category(category)
                .pinnedUntil(pinnedUntil)
                .build());
    }
}
