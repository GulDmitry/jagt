package dev.jagt.orchestrator.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Every {@link Job} there is, on one ticker. A job runs on its own thread, so one that takes minutes cannot hold
 * up the rest — and never concurrently with itself, so no job needs a guard of its own. A run that throws is
 * booked against that job and nothing else: unattended work must not be able to take the process down.
 */
@Component
@Slf4j
public class Jobs {

    /** What a human is owed about one job, whether or not it has ever run. */
    public record Status(String id, String describe, Duration every, Long lastStartedAt, String lastError,
                         Long nextRunAt, boolean running) {
    }

    private static final class Run {
        private final AtomicBoolean running = new AtomicBoolean();
        private volatile long nextAt;
        private volatile Long startedAt;
        private volatile String lastError;
    }

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final Executor workers;

    @org.springframework.beans.factory.annotation.Autowired
    public Jobs(List<Job> declared) {
        this(declared, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** A thread per run, so a job measured in minutes cannot hold up the ticker; direct in tests. */
    Jobs(List<Job> declared, Executor workers) {
        this.workers = workers;
        for (Job job : declared) {
            // A job that cannot name itself cannot be keyed, listed or reported on, so it does not run.
            if (job.id() == null || job.id().isBlank()) {
                log.warn("{} declares no job id — not registered", job.getClass().getSimpleName());
                continue;
            }
            if (jobs.put(job.id(), job) != null) {
                throw new IllegalStateException("Two jobs declare the id '" + job.id() + "'");
            }
            runs.put(job.id(), new Run());
        }
    }

    /** {@code now} is passed in, as it is to {@link #tick}, so a report reads the clock once and only outside. */
    public List<Status> statuses(long now) {
        return jobs.values().stream().map(job -> {
            Run run = runs.get(job.id());
            // Never behind the present: a job that has not run yet carries no schedule of its own, and its
            // honest next run is the tick about to happen.
            Long next = run.nextAt == Long.MAX_VALUE ? null : Math.max(run.nextAt, now);
            return new Status(job.id(), job.describe(), job.every(), run.startedAt, run.lastError, next,
                    run.running.get());
        }).toList();
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long now) {
        jobs.forEach((id, job) -> {
            Run run = runs.get(id);
            if (run.nextAt > now || !run.running.compareAndSet(false, true)) {
                return;
            }
            // Stamped before the run, not after: a job that takes longer than its interval is due again the
            // moment it finishes, which is what a rate rather than a delay means.
            run.startedAt = now;
            run.nextAt = job.every() == null ? Long.MAX_VALUE : now + job.every().toMillis();
            workers.execute(() -> {
                try {
                    job.run();
                    run.lastError = null;
                } catch (Throwable t) {
                    run.lastError = t.toString();
                    log.warn("job {} failed: {}", id, t.toString());
                } finally {
                    run.running.set(false);
                }
            });
        });
    }
}
