package com.kraft.report.domain;

/**
 * 신고의 처리 상태.
 * <p>
 * 처리한 뒤에도 행을 남기는 것이 중요하다 — 같은 대상이 반복해서 신고되는지, 관리자가 무엇을
 * 언제 지웠는지가 남아야 나중에 설명할 수 있다.
 */
public enum ReportStatus {

    /** 아직 관리자가 보지 않았다. 관리자 화면의 기본 목록이다. */
    PENDING,

    /** 문제가 없다고 판단했다. 대상은 그대로 둔다. */
    REJECTED,

    /** 신고를 받아들여 대상을 삭제했다. */
    RESOLVED
}
