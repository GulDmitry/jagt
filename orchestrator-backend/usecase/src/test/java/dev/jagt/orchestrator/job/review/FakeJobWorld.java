package dev.jagt.orchestrator.job.review;

import static dev.jagt.orchestrator.job.TestPollingTaskBuilder.pollingTask;
import static dev.jagt.orchestrator.job.TestYourMoveTaskBuilder.yourMoveTask;
import static dev.jagt.orchestrator.job.review.TestAutoReviewSchedulerBuilder.autoReviewScheduler;
import static dev.jagt.orchestrator.job.review.TestYourMoveJobBuilder.yourMoveJob;
import static dev.jagt.orchestrator.notify.TestPointWatchBuilder.pointWatch;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.notify.FakeNotificationSink;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.notify.PointWatch;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.ReviewSweepService;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.testing.FakeClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Two tasks over one state file, the jobs that tell about them, and a clock; a restart rebuilds
 * every service over the same file.
 */
public final class FakeJobWorld implements AutoCloseable {

  public enum Step {
    AUTO_REVIEW,
    YOUR_MOVE,
    POINT_WATCH,
    SECOND,
    MINUTE,
    HOUR,
    DAY,
    RESTART
  }

  public record Start(TaskStatus polling, long ageMinutes, TaskStatus waiting) {}

  private record Jobs(AutoReviewScheduler review, YourMoveJob yourMove, PointWatch points) {}

  private final Path root;
  private final FakeClock clock = new FakeClock();
  private final FakeNotificationSink sink = new FakeNotificationSink();
  private Jobs jobs;

  public FakeJobWorld(Start start) {
    try {
      root = Files.createTempDirectory("jagt-jobs");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    StateService state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-1",
        pollingTask()
            .withStatus(start.polling())
            .withMrCreatedAt(clock.millis() - Duration.ofMinutes(start.ageMinutes()).toMillis())
            .build());
    state.putTask("ABC-2", yourMoveTask().withStatus(start.waiting()).build());
    jobs = rebuilt(state);
  }

  private Jobs rebuilt(StateService state) {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults().withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
    ReviewSweepService sweep = mock(ReviewSweepService.class);
    when(sweep.sweep(anyString()))
        .thenReturn(
            new ReviewSweepService.SweepResult(ReviewSweepService.SweepResult.Kind.PENDING, ""));
    Notifications notifications = sink.notifications();
    return new Jobs(
        autoReviewScheduler().withRoot(root).build(state, config, sweep, Runnable::run, clock),
        yourMoveJob().build(state, notifications, mock(WorktreeChanges.class), config, clock),
        pointWatch().withRoot(root).build(state, notifications));
  }

  public void run(List<Step> steps) {
    steps.forEach(this::run);
  }

  public void run(Step step) {
    switch (step) {
      case AUTO_REVIEW -> jobs.review().run();
      case YOUR_MOVE -> jobs.yourMove().run();
      case POINT_WATCH -> jobs.points().sweep();
      case SECOND -> clock.advance(Duration.ofSeconds(7));
      case MINUTE -> clock.advance(Duration.ofMinutes(3));
      case HOUR -> clock.advance(Duration.ofHours(5));
      case DAY -> clock.advance(Duration.ofHours(25));
      default -> jobs = rebuilt(stateService().withRoot(root).build());
    }
  }

  public List<Notification> sent() {
    return sink.sent();
  }

  @Override
  public void close() {
    try (Stream<Path> files = Files.walk(root)) {
      files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
