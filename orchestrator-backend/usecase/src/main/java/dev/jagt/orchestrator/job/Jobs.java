package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.BoardStop;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;

/**
 * Every {@link Job} there is, each on the shared scheduler. A job never runs concurrently with
 * itself, and a run that throws is booked against that job alone.
 */
@Component
@Slf4j
public class Jobs {

  public record Status(
      String id,
      String describe,
      @Nullable Duration every,
      @Nullable Long lastStartedAt,
      @Nullable String lastError,
      @Nullable Long nextRunAt,
      boolean running,
      String serves) {}

  /**
   * {@code nextRunAt} is the soonest run of any job, or null when nothing is scheduled; {@code
   * failing} says each job whose last run threw, with its cause.
   */
  public record Summary(int count, @Nullable Long nextRunAt, List<String> failing) {}

  /** The declared jobs and those each {@link JobSource} holds. */
  @Component
  public static class Catalog {

    private final List<Job> all;

    public Catalog(List<Job> declared, List<JobSource> sources) {
      all = new ArrayList<>(declared);
      sources.forEach(source -> all.addAll(source.jobs()));
    }

    List<Job> all() {
      return List.copyOf(all);
    }
  }

  private static final class Run {
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long nextAt;
    private volatile @Nullable Long startedAt;
    private volatile @Nullable String lastError;
    private volatile long reportedAt;
    private final AtomicInteger repeats = new AtomicInteger();
    private volatile @Nullable Duration every;
    private volatile @Nullable ScheduledFuture<?> future;
  }

  private static final Duration RETRY = Duration.ofSeconds(1);
  private static final Duration REPEAT_REPORT = Duration.ofHours(1);

  private final Map<String, Job> jobs = new LinkedHashMap<>();
  private final Map<String, Run> runs = new LinkedHashMap<>();
  private final TaskScheduler scheduler;
  private final Clock clock;
  private final StateService stateService;
  private final BoardStop boardStop;
  private volatile boolean ready;

  public Jobs(
      Catalog catalog,
      TaskScheduler scheduler,
      Clock clock,
      StateService stateService,
      BoardStop boardStop) {
    this.scheduler = scheduler;
    this.clock = clock;
    this.stateService = stateService;
    this.boardStop = boardStop;
    for (Job job : catalog.all()) {
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
    return jobs.values().stream()
        .map(job -> {
          Run run = Objects.requireNonNull(runs.get(job.id()));
          // Never behind the present: a job that has not run yet is due at the tick about to
          // happen.
          Long next = run.nextAt == Long.MAX_VALUE ? null : Math.max(run.nextAt, now);
          return new Status(
              job.id(),
              job.describe(),
              every(job, run),
              run.startedAt,
              run.lastError,
              next,
              run.running.get(),
              Objects.requireNonNullElse(job.serves(), Job.Serves.HOUSEKEEPING).label());
        })
        .toList();
  }

  /**
   * The last interval a job named stands in for one it cannot: a report answers for all of them.
   */
  private static @Nullable Duration every(Job job, Run run) {
    try {
      return job.every();
    } catch (RuntimeException e) {
      return run.every;
    }
  }

  public Summary summary(long now) {
    List<Status> all = statuses(now);
    return new Summary(
        all.size(),
        all.stream()
            .map(Status::nextRunAt)
            .filter(Objects::nonNull)
            .min(Long::compareTo)
            .orElse(null),
        all.stream()
            .filter(status -> status.lastError() != null)
            .map(status -> status.id() + ": " + status.lastError())
            .toList());
  }

  public void runNow(String id) {
    Run run = runs.get(id);
    if (run == null) {
      throw new IllegalArgumentException(
          "No job '" + id + "' — `jobs` lists them: " + String.join(", ", runs.keySet()));
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

  /**
   * Stamped from the start of the last run, not its end: a job that outlasts its interval is due
   * again at once.
   */
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
    if (boardStop.stopped()) {
      return;
    }
    if (!run.running.compareAndSet(false, true)) {
      return;
    }
    run.startedAt = clock.millis();
    try {
      runOnce(job);
      recovered(job.id(), run);
    } catch (SessionHost.WindowsUnreadable unknown) {
      // Already logged where it was read: this round decides nothing and the next one asks again.
    } catch (Processes.Stopped unjudged) {
      log.atInfo()
          .setMessage("job stopped with jagt")
          .addKeyValue("job", job.id())
          .addKeyValue("effect", "nothing written, the next start asks again")
          .log();
    } catch (Throwable t) { // any failure is logged, never allowed to end the schedule
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

  /**
   * A cause already reported is repeated at DEBUG, and at ERROR with its count once per {@link
   * #REPEAT_REPORT}: a job failing on every tick would bury the log, one line would hide it.
   */
  private void fail(String id, Run run, Throwable t) {
    String cause = t.toString();
    boolean reported = cause.equals(run.lastError);
    run.lastError = cause;
    long now = clock.millis();
    if (reported) {
      run.repeats.incrementAndGet();
    } else {
      run.repeats.set(0);
      run.reportedAt = now;
    }
    boolean due = reported && now - run.reportedAt >= REPEAT_REPORT.toMillis();
    var line = reported && !due ? log.atDebug() : log.atError();
    line = line.setMessage("job failed").addKeyValue("job", id).addKeyValue("cause", cause);
    if (due) {
      line = line.addKeyValue("repeats", run.repeats.get());
      run.repeats.set(0);
      run.reportedAt = now;
    }
    line.log();
  }

  private static void recovered(String id, Run run) {
    String previous = run.lastError;
    run.lastError = null;
    if (previous != null) {
      log.atInfo()
          .setMessage("job recovered")
          .addKeyValue("job", id)
          .addKeyValue("previous", previous)
          .log();
    }
  }
}
