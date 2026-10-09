package com.kraft.support;

/**
 * Testcontainers 테스트가 쓰는 MariaDB 이미지의 단일 출처. 운영({@code docker-compose.yml})과 같은
 * 버전을 써야 한다 — 운영과 다른 DB를 검증하면 마이그레이션 테스트가 의미가 없다.
 * {@code MariaDbImageSyncTest}가 이 값과 docker-compose.yml이 같은지 확인한다.
 */
public final class MariaDbImage {

    public static final String NAME = "mariadb:11.7.2";

    private MariaDbImage() {
    }
}
