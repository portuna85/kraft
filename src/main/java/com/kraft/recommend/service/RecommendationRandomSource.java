package com.kraft.recommend.service;

import java.util.Random;

/**
 * 후보 생성에 쓰는 난수 공급자. 운영 구현은 호출 스레드에서 난수에 접근하고, 테스트는 고정
 * 시드로 교체해 과거 충돌·동점·상한 상황을 재현한다(02문서 3절).
 */
public interface RecommendationRandomSource {

    Random current();
}
