package dev.jagt.orchestrator.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Small enough to sit in a header, so unattended work is visible without being asked for.
     *
     * @param nextRunAt the soonest run of any job, or null when nothing is scheduled
     */
    public record Summary(int count, Long nextRunAt, int failing) {
    }

    private static final class Run {
        private final AtomicBoolean running = new AtomicBoolean();
        private volatile long nextAt;
        private volatile Long startedAt;
        private volatile String lastError;
        private volatile Duration every;
    }

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final Executor workers;

    @org.springframework.beans.factory.annotation.Autowired
    public Jobs(List<Job> declared) {
        this(declared, Executors.newVirtualThreadPerTaskExecutor());
    }

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
            return new Status(job.id(), job.describe(), every(job, run), run.startedAt, run.lastError, next,
                    run.running.get());
        }).toList();
    }

    /** The last interval a job managed to name stands in for one it cannot: a report answers for all of them. */
    private static Duration every(Job job, Run run) {
        try {
            return job.every();
        } catch (RuntimeException e) {
            return run.every;
        }
    }

    public Summary summary(long now) {
        List<Status> all = statuses(now);
        return new Summary(all.size(),
                all.stream().map(Status::nextRunAt).filter(Objects::nonNull).min(Long::compareTo).orElse(null),
                (int) all.stream().filter(status -> status.lastError() != null).count());
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
            run.startedAt = now;
            workers.execute(() -> {
                try {
                    // Asked in here rather than before the dispatch, so an interval that cannot be answered
                    // is booked like any other failed run and leaves the job due instead of stuck. Stamped
                    // from the tick and not from the finish: a job that takes longer than its interval is due
                    // again the moment it ends, which is what a rate rather than a delay means.
                    run.every = job.every();
                    run.nextAt = run.every == null ? Long.MAX_VALUE : now + run.every.toMillis();
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
