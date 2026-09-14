package com.kraft.post.service;

import com.kraft.post.domain.Category;
import com.kraft.user.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CategoryPolicy} 단위 테스트. 공지(NOTICE)를 관리자 전용으로 제한하는 정책이다 —
 * 예전에는 서버에 이 제한이 없어 일반 사용자도 NOTICE로 글을 쓸 수 있었다.
 */
class CategoryPolicyTest {

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"USER", "GUEST"})
    @DisplayName("관리자가 아니면 NOTICE를 쓸 수 없다")
    void requireCanUse_whenNotAdmin_throwsForNotice(Role role) {
        assertThatThrownBy(() -> CategoryPolicy.requireCanUse(authOf(role), Category.NOTICE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("공지 분류는 관리자만");
    }

    @Test
    @DisplayName("관리자는 NOTICE를 쓸 수 있다")
    void requireCanUse_whenAdmin_allowsNotice() {
        assertThatCode(() -> CategoryPolicy.requireCanUse(authOf(Role.ADMIN), Category.NOTICE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"FREE", "QNA"})
    @DisplayName("NOTICE가 아닌 분류는 누구나 쓸 수 있다")
    void requireCanUse_withOtherCategories_allowsAnyone(Category category) {
        assertThatCode(() -> CategoryPolicy.requireCanUse(authOf(Role.USER), category))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("분류를 지정하지 않으면(null) 통과한다 — 기본값 FREE로 저장된다")
    void requireCanUse_whenCategoryIsNull_allows() {
        assertThatCode(() -> CategoryPolicy.requireCanUse(authOf(Role.USER), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("익명·인증 정보 없음이면 NOTICE를 쓸 수 없다")
    void requireCanUse_whenAnonymousOrNull_throwsForNotice() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> CategoryPolicy.requireCanUse(anonymous, Category.NOTICE))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> CategoryPolicy.requireCanUse(null, Category.NOTICE))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Authentication authOf(Role role) {
        return new UsernamePasswordAuthenticationToken("someone@example.com", null,
                List.of(new SimpleGrantedAuthority(role.getKey())));
    }
}
