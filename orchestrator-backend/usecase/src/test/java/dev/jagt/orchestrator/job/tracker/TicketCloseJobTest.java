package dev.jagt.orchestrator.job.tracker;

import static dev.jagt.orchestrator.job.TestTicketCloseTaskBuilder.ticketCloseTask;
import static dev.jagt.orchestrator.job.tracker.TestTicketCloseJobBuilder.ticketCloseJob;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketCloseJobTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final TrackerClose close = mock(TrackerClose.class);
  private final CommandService commands = mock(CommandService.class);

  @TempDir Path root;

  private StateService stateService;
  private TicketCloseJob job;

  @BeforeEach
  void trackerWatchingAnEmptyState() {
    stateService = stateService().withRoot(root).build();
    when(configService.load()).thenReturn(ticketCloseJob().config());
    job = ticketCloseJob().build(configService, stateService, close, commands, new FakeClock());
  }

  @Test
  void asksAboutNothingWhileIntakeIsOff() {
    when(configService.load()).thenReturn(ConfigFile.defaults());

    job.run();

    verify(close, never()).closes(anyString(), any());
  }

  @Test
  void asksAboutNoTaskWhoseWorkIsStillInItsWorktree() {
    stateService.putTask("ABC-42", ticketCloseTask().withStatus(TaskStatus.IN_PROGRESS).build());

    job.run();

    verify(close, never()).closes(anyString(), any());
  }

  @Test
  void closesTheTaskTheTrackerSaysIsFinishedWith() {
    when(close.watches("ABC-42")).thenReturn(true);
    TaskState task = ticketCloseTask().build();
    stateService.putTask("ABC-42", task);
    when(close.closes("ABC-42", task)).thenReturn(true);

    job.run();

    verify(commands).execute("ABC-42", TaskAction.DONE);
  }

  @Test
  void leavesTheSixthTaskUnaskedWithinOnePollAndReachesItOnTheNextPoll() {
    when(close.watches("ABC-1")).thenReturn(true);
    when(close.watches("ABC-2")).thenReturn(true);
    when(close.watches("ABC-3")).thenReturn(true);
    when(close.watches("ABC-4")).thenReturn(true);
    when(close.watches("ABC-5")).thenReturn(true);
    when(close.watches("ABC-6")).thenReturn(true);
    TaskState task = ticketCloseTask().build();
    stateService.putTask("ABC-1", task);
    stateService.putTask("ABC-2", task);
    stateService.putTask("ABC-3", task);
    stateService.putTask("ABC-4", task);
    stateService.putTask("ABC-5", task);
    stateService.putTask("ABC-6", task);

    job.run();
    verify(close, never()).closes("ABC-6", task);
    job.run();

    verify(close).closes("ABC-6", task);
  }

  @Test
  void buysNoSecondReadAboutTaskThatHasNotMovedSinceItWasAsked() {
    when(close.watches("ABC-42")).thenReturn(true);
    TaskState task =
        ticketCloseTask()
            .withHistory(List.of(new StatusChange(TaskStatus.DEPLOYED, 1_000L, null)))
            .build();
    stateService.putTask("ABC-42", task);
    when(close.closes("ABC-42", task)).thenReturn(false);

    job.run();
    job.run();

    verify(close, times(1)).closes("ABC-42", task);
  }

  @Test
  void asksAgainTheMomentTheTaskItselfHasMoved() {
    when(close.watches("ABC-42")).thenReturn(true);
    stateService.putTask(
        "ABC-42",
        ticketCloseTask()
            .withHistory(List.of(new StatusChange(TaskStatus.DEPLOYED, 1_000L, null)))
            .build());
    final TaskState moved =
        ticketCloseTask()
            .withHistory(List.of(new StatusChange(TaskStatus.DEPLOYED, 2_000L, null)))
            .build();
    when(close.closes(anyString(), any())).thenReturn(false);
    job.run();
    stateService.putTask("ABC-42", moved);

    job.run();

    verify(close).closes("ABC-42", moved);
  }

  @Test
  void leavesOpenTheTaskTheTrackerHasNotFinishedWith() {
    TaskState task = ticketCloseTask().build();
    stateService.putTask("ABC-42", task);
    when(close.closes("ABC-42", task)).thenReturn(false);

    job.run();

    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void asksMissedTaskAgainAfterTheIntervalThenAfterTwiceAsLongThenAfterFourTimes() {
    when(close.watches("ABC-42")).thenReturn(true);
    TaskState task =
        ticketCloseTask()
            .withHistory(List.of(new StatusChange(TaskStatus.DEPLOYED, 1_000L, null)))
            .build();
    ConfigFile every10 =
        ticketCloseJob().withTracker(ticketCloseJob().tracker().withEveryMinutes(10)).config();
    when(configService.load()).thenReturn(every10);
    stateService.putTask("ABC-42", task);
    when(close.closes("ABC-42", task)).thenReturn(false);
    FakeClock clock = new FakeClock();
    TicketCloseJob ticking =
        ticketCloseJob().build(configService, stateService, close, commands, clock);

    ticking.run();
    clock.advance(Duration.ofMinutes(10));
    ticking.run();
    clock.advance(Duration.ofMinutes(19));
    ticking.run();
    clock.advance(Duration.ofMinutes(1));
    ticking.run();
    clock.advance(Duration.ofMinutes(10));
    ticking.run();

    verify(close, times(3)).closes("ABC-42", task);
  }
}
