package com.kraft.community.post;

/** 게시글 카테고리 5종. 기존 글은 마이그레이션(V21)에서 GENERAL로 보정된다. */
public enum PostCategory {
    RECOMMENDATION_SHARE,
    ROUND_ANALYSIS,
    WIN_STORY,
    QUESTION,
    GENERAL
}
