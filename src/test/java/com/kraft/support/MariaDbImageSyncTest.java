package com.kraft.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 테스트 DB 이미지가 운영(docker-compose.yml)과 같은 버전이다. 한쪽만 올리면 여기서 깨진다. */
class MariaDbImageSyncTest {

    @Test
    @DisplayName("Testcontainers의 MariaDB 이미지가 docker-compose.yml의 mariadb 서비스와 같다")
    void testImageMatchesDockerCompose() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"), StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile("(?m)^\\s*image:\\s*(mariadb:[\\w.\\-]+)\\s*$").matcher(compose);

        assertThat(matcher.find()).as("docker-compose.yml에 mariadb 이미지가 있어야 한다").isTrue();
        assertThat(MariaDbImage.NAME).isEqualTo(matcher.group(1));
    }
}
