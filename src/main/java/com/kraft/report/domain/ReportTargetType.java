package com.kraft.report.domain;

/**
 * 신고 대상의 종류. 값 목록은 Hibernate가 MariaDB에 만드는 네이티브 ENUM과 같도록 알파벳
 * 순으로 쓴다(V1__baseline.sql의 users.role 주석 참고).
 */
public enum ReportTargetType {

    COMMENT("댓글"),
    POST("게시글");

    private final String title;

    ReportTargetType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
