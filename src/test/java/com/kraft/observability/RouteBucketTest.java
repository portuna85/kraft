package com.kraft.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RouteBucketTest {

    @ParameterizedTest
    @CsvSource({
            "/, HOME",
            "/recommend, RECOMMEND",
            "/recommend/history, RECOMMEND_HISTORY",
            "/community, COMMUNITY",
            "/community/write, COMMUNITY_WRITE",
            "/community/posts/1150, COMMUNITY_POST",
            "/community/posts/1150/edit, COMMUNITY_POST",
            "/companion, COMPANION",
            "/frequency, FREQUENCY",
            "/info/about, INFO",
            "/ops, OPS",
            "/saved, SAVED",
            "/stats, STATS",
            "/status, STATUS",
            "/data, DATA",
            "/analysis, ANALYSIS"
    })
    void of_knownPaths_mapsToExpectedBucket(String path, RouteBucket expected) {
        assertThat(RouteBucket.of(path)).isEqualTo(expected);
    }

    @Test
    void of_unknownPath_mapsToOther() {
        assertThat(RouteBucket.of("/totally/unknown/" + "path")).isEqualTo(RouteBucket.OTHER);
    }

    @Test
    void of_null_mapsToOther() {
        assertThat(RouteBucket.of(null)).isEqualTo(RouteBucket.OTHER);
    }
}
