package com.kraft.recommend.domain;

/** 동행복권 공식 배색(1~10 노랑·11~20 파랑·21~30 빨강·31~40 회색·41~45 초록)에 맞춘 구간 색상. */
public final class LottoBallColor {

    private LottoBallColor() {
    }

    public static String cssClass(int n) {
        return "lotto-ball--" + colorName(n);
    }

    /** 공용 {@code lotto-ball--*} 클래스를 쓰는 화면이 접미사만 가져다 쓴다. */
    public static String colorName(int n) {
        if (n <= 10) {
            return "yellow";
        }
        if (n <= 20) {
            return "blue";
        }
        if (n <= 30) {
            return "red";
        }
        if (n <= 40) {
            return "gray";
        }
        return "green";
    }
}
