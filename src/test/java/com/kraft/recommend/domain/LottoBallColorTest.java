package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link LottoBallColor}의 구간 경계값을 확인한다. */
class LottoBallColorTest {

    @Test
    @DisplayName("1~10은 노랑")
    void yellowBand() {
        assertThat(LottoBallColor.cssClass(1)).isEqualTo("recommend__latest-ball--yellow");
        assertThat(LottoBallColor.cssClass(10)).isEqualTo("recommend__latest-ball--yellow");
    }

    @Test
    @DisplayName("11~20은 파랑")
    void blueBand() {
        assertThat(LottoBallColor.cssClass(11)).isEqualTo("recommend__latest-ball--blue");
        assertThat(LottoBallColor.cssClass(20)).isEqualTo("recommend__latest-ball--blue");
    }

    @Test
    @DisplayName("21~30은 빨강")
    void redBand() {
        assertThat(LottoBallColor.cssClass(21)).isEqualTo("recommend__latest-ball--red");
        assertThat(LottoBallColor.cssClass(30)).isEqualTo("recommend__latest-ball--red");
    }

    @Test
    @DisplayName("31~40은 회색")
    void grayBand() {
        assertThat(LottoBallColor.cssClass(31)).isEqualTo("recommend__latest-ball--gray");
        assertThat(LottoBallColor.cssClass(40)).isEqualTo("recommend__latest-ball--gray");
    }

    @Test
    @DisplayName("41~45는 초록")
    void greenBand() {
        assertThat(LottoBallColor.cssClass(41)).isEqualTo("recommend__latest-ball--green");
        assertThat(LottoBallColor.cssClass(45)).isEqualTo("recommend__latest-ball--green");
    }
}
