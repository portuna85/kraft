package com.kraft.report.domain;

/**
 * 신고 사유. 신고자가 고르는 값이며, 관리자가 목록에서 무엇부터 볼지 판단하는 근거가 된다.
 * 자세한 사정은 detail에 적는다.
 */
public enum ReportReason {

    ABUSE("욕설·비방"),
    OTHER("기타"),
    SEXUAL("음란물"),
    SPAM("스팸·광고");

    private final String title;

    ReportReason(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
