package dev.jagt.orchestrator;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** What a bean needs from the runtime and cannot make itself: a clock, a client, a pool, a policy. */
@Configuration
public class RuntimeWiring {

    private static final int NOTIFICATION_BACKLOG = 64;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Bean
    public RetryPolicy paidRead() {
        return RetryPolicy.PAID_READ;
    }

    @Bean(destroyMethod = "")
    public Executor notificationDelivery() {
        return new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(NOTIFICATION_BACKLOG),
                Thread.ofPlatform().name("jagt-notify-", 0).daemon().factory());
    }

    /** Serial: a sweep runs for minutes, so polls must not pile up behind it. */
    @Bean(destroyMethod = "")
    public Executor autoReviewSweeps() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "auto-review");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "")
    public ExecutorService boardBroadcaster() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "board-sse");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** One thread per job, which holds it for the whole run, and one for the board's heartbeat. */
    @Bean
    public TaskScheduler taskScheduler(List<Job> jobs, Clock clock) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(jobs.size() + 1);
        scheduler.setClock(clock);
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }
}
