package com.kraft.recommend.domain;

/** 동행복권 공식 배색(1~10 노랑·11~20 파랑·21~30 빨강·31~40 회색·41~45 초록)에 맞춘 구간 색상. */
public final class LottoBallColor {

    private LottoBallColor() {
    }

    public static String cssClass(int n) {
        if (n <= 10) {
            return "recommend__latest-ball--yellow";
        }
        if (n <= 20) {
            return "recommend__latest-ball--blue";
        }
        if (n <= 30) {
            return "recommend__latest-ball--red";
        }
        if (n <= 40) {
            return "recommend__latest-ball--gray";
        }
        return "recommend__latest-ball--green";
    }
}
