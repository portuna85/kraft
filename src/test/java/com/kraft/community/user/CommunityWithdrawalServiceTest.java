package com.kraft.community.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kraft.common.error.ApiException;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityWithdrawalService 단위 테스트")
class CommunityWithdrawalServiceTest {

    @Mock
    private CommunityUserRepository communityUserRepository;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    private CommunityWithdrawalService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityWithdrawalService(
                communityUserRepository, communityPostRepository, communityCommentRepository, clock);
    }

    @Test
    @DisplayName("탈퇴 시 닉네임 익명화·프로필 이미지 제거·게시글·댓글 작성자 표기 일괄 재작성을 수행한다")
    void withdraw_pseudonymizesUserAndRewritesExistingContent() {
        CommunityUser user = new CommunityUser("google", "sub-1", "원래닉네임", "https://img", OffsetDateTime.now());
        setId(user, 42L);
        given(communityUserRepository.lockById(42L)).willReturn(Optional.of(user));

        service.withdraw(42L);

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getNickname()).isEqualTo("탈퇴한 사용자#42");
        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.getWithdrawnAt()).isEqualTo(OffsetDateTime.parse("2026-07-30T00:00:00Z"));
        verify(communityUserRepository).save(user);
        verify(communityPostRepository).renameAuthorForOwner(42L, "탈퇴한 사용자#42");
        verify(communityCommentRepository).renameAuthorForOwner(42L, "탈퇴한 사용자#42");
    }

    @Test
    @DisplayName("이미 탈퇴한 계정을 다시 탈퇴시켜도 재작성이 일어나지 않는다(멱등)")
    void withdraw_isIdempotentForAlreadyWithdrawnUser() {
        CommunityUser user = new CommunityUser("google", "sub-1", "탈퇴한 사용자#42", null, OffsetDateTime.now());
        setId(user, 42L);
        user.withdraw("탈퇴한 사용자#42", OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        given(communityUserRepository.lockById(42L)).willReturn(Optional.of(user));

        service.withdraw(42L);

        assertThat(user.getWithdrawnAt()).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        verify(communityUserRepository, never()).save(user);
        verify(communityPostRepository, never()).renameAuthorForOwner(42L, "탈퇴한 사용자#42");
        verify(communityCommentRepository, never()).renameAuthorForOwner(42L, "탈퇴한 사용자#42");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 탈퇴는 404로 변환된다")
    void withdraw_unknownUser_throwsNotFound() {
        given(communityUserRepository.lockById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void setId(CommunityUser user, Long id) {
        try {
            var field = CommunityUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
