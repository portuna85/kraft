package com.kraft.recommend.service;

import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ThreadLocalRecommendationRandomSource implements RecommendationRandomSource {

    @Override
    public Random current() {
        return ThreadLocalRandom.current();
    }
}
