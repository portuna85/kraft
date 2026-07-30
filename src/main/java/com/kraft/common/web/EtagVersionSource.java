package com.kraft.common.web;

/**
 * KB-17: {@code common}은 특정 기능 패키지를 몰라야 하는 기반 계층이라, 도메인 버전(회차
 * 번호)에서 파생한 ETag 계산 자체는 그 도메인을 아는 패키지(winningnumber)에 두고 이
 * 인터페이스로만 의존한다. {@link PublicApiCacheControlFilter}는 구현체가 아니라 이
 * 계약에만 의존한다.
 */
public interface EtagVersionSource {

    /**
     * 경로에 맞는 ETag를 반환한다. null 반환 시 호출자가 MD5 폴백(바디 해시)을 적용해야 한다.
     */
    String etagForPath(String requestPath);
}
