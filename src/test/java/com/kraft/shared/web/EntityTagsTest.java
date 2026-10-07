package com.kraft.shared.web;

import com.kraft.shared.exception.PreconditionRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityTagsTest {

    @Test
    @DisplayName("of: 버전을 강한 ETag(따옴표로 감싼 값)로 만든다")
    void of_buildsStrongEtag() {
        assertThat(EntityTags.of(3L)).isEqualTo("\"3\"");
    }

    @Test
    @DisplayName("expectedVersion: 강한 표기와 약한 표기(W/) 모두 같은 버전으로 읽는다")
    void expectedVersion_readsStrongAndWeakForms() {
        assertThat(EntityTags.expectedVersion("\"3\"")).isEqualTo(3L);
        assertThat(EntityTags.expectedVersion("W/\"3\"")).isEqualTo(3L);
        assertThat(EntityTags.expectedVersion("  \"12\"  ")).isEqualTo(12L);
        assertThat(EntityTags.expectedVersion("\"0\"")).isZero();
    }

    @Test
    @DisplayName("expectedVersion: 헤더가 없거나 비어 있으면 428용 예외다 — 본문 version은 더 이상 읽지 않는다")
    void expectedVersion_withoutHeader_throwsPreconditionRequired() {
        assertThatThrownBy(() -> EntityTags.expectedVersion(null))
                .isInstanceOf(PreconditionRequiredException.class);
        assertThatThrownBy(() -> EntityTags.expectedVersion(""))
                .isInstanceOf(PreconditionRequiredException.class);
        assertThatThrownBy(() -> EntityTags.expectedVersion("   "))
                .isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    @DisplayName("expectedVersion: If-Match: * 는 존재하기만 하면 된다는 뜻이라 검사를 생략한다(null)")
    void expectedVersion_star_skipsCheck() {
        assertThat(EntityTags.expectedVersion("*")).isNull();
    }

    @Test
    @DisplayName("expectedVersion: 형식이 틀리거나 여러 값을 나열하면 어떤 버전과도 맞지 않는 값이라 412로 이어진다")
    void expectedVersion_malformed_neverMatches() {
        for (String malformed : new String[]{"3", "\"abc\"", "\"\"", "\"3", "\"1\", \"2\"", "\"-1\"",
                "\"99999999999999999999\"", "W/3"}) {
            assertThat(EntityTags.expectedVersion(malformed))
                    .as(malformed).isEqualTo(EntityTags.NO_MATCH);
        }
    }
}
