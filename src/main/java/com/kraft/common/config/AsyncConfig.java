package com.kraft.common.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncFailureMonitor asyncFailureMonitor;

    public AsyncConfig(AsyncFailureMonitor asyncFailureMonitor) {
        this.asyncFailureMonitor = asyncFailureMonitor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return asyncFailureMonitor;
    }

    @Bean(name = "eventTaskExecutor")
    ThreadPoolTaskExecutor eventTaskExecutor() {
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("kraft-evt-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        // 큐 포화 시 기본 AbortPolicy는 이벤트를 조용히 유실한다.
        // CallerRuns는 발행 스레드에서 동기 처리해 유실 없이 역압을 전파한다.
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

    /**
     * 전체 회차 수집(백필) 전용 단일 스레드 풀.
     * 수 분이 걸리는 장시간 작업이 이벤트 처리 풀을 점유하지 않도록 분리한다.
     * 동시 실행 차단은 이제 {@code WinningNumberBackfillService.tryStart()}의 동기 CAS가
     * 시작 예약 단계에서 담당하므로, 이 풀에 두 번째 태스크가 도달하는 일은 정상 경로에서는
     * 없다. 큐 0 + AbortPolicy로 만약 도달하면 조용히 유실(DiscardPolicy)하는 대신 예외를
     * 던져 호출자(AdminController)가 running 플래그를 되돌리고 사용자에게 알리게 한다.
     */
    @Bean(name = "backfillTaskExecutor")
    ThreadPoolTaskExecutor backfillTaskExecutor() {
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(0);
        ex.setThreadNamePrefix("kraft-backfill-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(60);
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }
}
