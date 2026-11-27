package dev.jagt.orchestrator.job.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.launch.ProjectRouting;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.Launched;
import dev.jagt.orchestrator.task.ticket.TicketFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeJobFaults;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntakeStartTest {

  private final ProjectRouting routing = mock(ProjectRouting.class);
  private final IntakeHistory history = mock(IntakeHistory.class);
  private final TaskLauncher launcher = mock(TaskLauncher.class);
  private final FakeJobFaults faults = new FakeJobFaults();
  private final IntakeStart starts = new IntakeStart(routing, history, launcher, faults);

  @Test
  void startsTheTaskInTheProjectTheItemWasPlacedIn() {
    TicketFacts item =
        TicketFacts.defaults().withExists(true).withKey("ABC-42").withLabels(List.of("backend"));
    when(routing.projectFor(item)).thenReturn(new ProjectRouting.Placed(List.of("api")));
    when(launcher.launch(any(), any())).thenReturn(Launched.created("ABC-42", "started"));

    boolean goesOn = starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

    assertThat(goesOn).isTrue();
    verify(launcher)
        .launch(
            LaunchRequest.of("ABC-42").withProject("api"),
            new Answer<>(Optional.of(item), TokenUsage.NONE));
  }

  @Test
  void turnsAwayAnItemNothingCouldPlaceInProject() {
    TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42");
    when(routing.projectFor(item))
        .thenReturn(new ProjectRouting.Undecided("placed in no configured project"));

    boolean goesOn = starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

    assertThat(goesOn).isTrue();
    verify(history).turnAway(eq("ABC-42"), anyString());
    verify(launcher, never()).launch(any(), any());
  }

  @Test
  void stopsThePollWhereTheBoardRefusedTheLaunch() {
    TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1");
    when(routing.projectFor(item)).thenReturn(new ProjectRouting.Placed(List.of("api")));
    when(launcher.launch(any(), any()))
        .thenThrow(new IllegalArgumentException("24 tasks are already open, which is the limit"));

    boolean goesOn = starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

    assertThat(goesOn).isFalse();
  }

  @Test
  void namesTheFailuresTypeAndKeepsItsStackWhereTheLaunchFailedWithoutMessage() {
    TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1");
    when(routing.projectFor(item)).thenReturn(new ProjectRouting.Placed(List.of("api")));
    when(launcher.launch(any(), any())).thenThrow(new NullPointerException());
    try (FakeLogCapture log = new FakeLogCapture(IntakeStart.class)) {
      starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

      ILoggingEvent failed =
          assertThat(log.events())
              .filteredOn(ILoggingEvent::getMessage, "intake could not start a task")
              .singleElement()
              .actual();
      assertThat(failed.getKeyValuePairs())
          .anySatisfy(pair -> {
            assertThat(pair.key).isEqualTo("cause");
            assertThat(pair.value).isEqualTo("NullPointerException");
          });
      assertThat(failed.getThrowableProxy()).isNotNull();
      assertThat(faults.causes()).containsExactly("NullPointerException");
    }
  }

  @Test
  void leavesAnItemTheRouterCouldNotAnswerForTheNextPollInsteadOfTurningItAway() {
    TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42");
    when(routing.projectFor(item))
        .thenReturn(new ProjectRouting.Unreadable("the router answered nothing"));

    boolean goesOn = starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

    assertThat(goesOn).isFalse();
    verify(history, never()).turnAway(anyString(), anyString());
    verify(launcher, never()).launch(any(), any());
  }

  @Test
  void turnsAwayAnItemTheLauncherDeclinedAndGoesOnToTheNext() {
    TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42");
    when(routing.projectFor(item)).thenReturn(new ProjectRouting.Placed(List.of("api")));
    when(launcher.launch(any(), any())).thenReturn(Launched.refused("ABC-42 is already a task"));

    boolean goesOn = starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

    assertThat(goesOn).isTrue();
    verify(history).turnAway("ABC-42", "ABC-42 is already a task");
  }
}
