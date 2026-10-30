package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Every {@link Job} there is, each on the shared scheduler. A job never runs concurrently with itself, and a run
 * that throws is booked against that job alone.
 */
@Component
@Slf4j
public class Jobs {

    public record Status(String id, String describe, @Nullable Duration every, @Nullable Long lastStartedAt,
                         @Nullable String lastError, @Nullable Long nextRunAt, boolean running, String serves) {
    }

    /** {@code nextRunAt} is the soonest run of any job, or null when nothing is scheduled. */
    public record Summary(int count, @Nullable Long nextRunAt, int failing) {
    }

    private static final class Run {
        private final AtomicBoolean running = new AtomicBoolean();
        private volatile long nextAt;
        private volatile @Nullable Long startedAt;
        private volatile @Nullable String lastError;
        private volatile @Nullable Duration every;
        private volatile @Nullable ScheduledFuture<?> future;
    }

    private static final Duration RETRY = Duration.ofSeconds(1);

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final StateService stateService;
    private volatile boolean ready;

    public Jobs(List<Job> declared, TaskScheduler scheduler, Clock clock, StateService stateService) {
        this.scheduler = scheduler;
        this.clock = clock;
        this.stateService = stateService;
        for (Job job : declared) {
            if (job.id() == null || job.id().isBlank()) {
                throw new IllegalStateException(job.getClass().getSimpleName() + " declares no id");
            }
            if (jobs.put(job.id(), job) != null) {
                throw new IllegalStateException("Two jobs declare the id '" + job.id() + "'");
            }
            runs.put(job.id(), new Run());
        }
    }

    /** {@code now} is passed in so a report reads the clock once, outside. */
    public List<Status> statuses(long now) {
        return jobs.values().stream().map(job -> {
            Run run = Objects.requireNonNull(runs.get(job.id()));
            // Never behind the present: a job that has not run yet is due at the tick about to happen.
            Long next = run.nextAt == Long.MAX_VALUE ? null : Math.max(run.nextAt, now);
            return new Status(job.id(), job.describe(), every(job, run), run.startedAt, run.lastError, next,
                    run.running.get(), Objects.requireNonNullElse(job.serves(), Job.Serves.HOUSEKEEPING).label());
        }).toList();
    }

    /** The last interval a job named stands in for one it cannot: a report answers for all of them. */
    private static @Nullable Duration every(Job job, Run run) {
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

    public void runNow(String id) {
        Run run = runs.get(id);
        if (run == null) {
            throw new IllegalArgumentException("No job '" + id + "' — `jobs` lists them: " + String.join(", ",
                    runs.keySet()));
        }
        if (run.running.get()) {
            throw new IllegalStateException("'" + id + "' is running now");
        }
        synchronized (run) {
            run.nextAt = 0;
            if (ready) {
                cancel(run);
                schedule(Objects.requireNonNull(jobs.get(id)));
            }
        }
    }

    private static void cancel(Run run) {
        ScheduledFuture<?> pending = run.future;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ready = true;
        jobs.values().forEach(this::schedule);
    }

    private void schedule(Job job) {
        Run run = Objects.requireNonNull(runs.get(job.id()));
        run.future = scheduler.schedule(() -> execute(job, run), context -> next(job, run, context));
    }

    /** Stamped from the start of the last run, not its end: a job that outlasts its interval is due again at once. */
    private @Nullable Instant next(Job job, Run run, TriggerContext context) {
        Instant started = context.lastActualExecution();
        if (started == null) {
            return clock.instant();
        }
        try {
            run.every = job.every();
        } catch (RuntimeException e) {
            fail(job.id(), run, e);
            return started.plus(RETRY);
        }
        if (run.every == null) {
            run.nextAt = Long.MAX_VALUE;
            return null;
        }
        Instant next = started.plus(run.every);
        run.nextAt = next.toEpochMilli();
        return next;
    }

    private void execute(Job job, Run run) {
        if (!run.running.compareAndSet(false, true)) {
            return;
        }
        run.startedAt = clock.millis();
        try {
            runOnce(job);
            run.lastError = null;
        } catch (SessionHost.WindowsUnreadable unknown) {
            // Already logged where it was read: this round decides nothing and the next one asks again.
        } catch (Throwable t) {
            fail(job.id(), run, t);
        } finally {
            run.running.set(false);
        }
    }

    private void runOnce(Job job) {
        if (job instanceof TaskJob taskJob) {
            taskJob.runOn(stateService.tasks());
        } else {
            job.run();
        }
    }

    private static void fail(String id, Run run, Throwable t) {
        run.lastError = t.toString();
        log.atError().setMessage("job failed")
                .addKeyValue("job", id)
                .addKeyValue("cause", t.toString())
                .log();
    }
}
