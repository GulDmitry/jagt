package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestRestartAskJobBuilder.restartAskJob;
import static dev.jagt.orchestrator.job.TestTaskTurnsBuilder.taskTurns;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestartAskJobTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final AgentPresence presence = mock(AgentPresence.class);
  private final StepRelay relay = mock(StepRelay.class);

  @TempDir Path root;

  private StateService state;
  private TaskTurns turns;
  private RestartAskJob job;

  @BeforeEach
  void restartedJagtWithOneTaskOnItsFirstTurn() {
    state = stateService().withRoot(root).build();
    turns = taskTurns().build(state, Clock.systemUTC());
    job = restartAskJob().withRoot(root).build(sessions, presence, relay, Clock.systemUTC());
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    turns.opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
  }

  @Test
  void asksRunningSessionHoldingAnOpenTurnToRepeatItsReport() throws IOException {
    Files.writeString(root.resolve("alive"), "2026-11-05T07:00:00Z");
    turns.opened("ABC-42", "round-not-ready", "digest", TaskStatus.REVIEW_PENDING);
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.loggedSince("ABC-42", 1793862000000L)).thenReturn(true);
    job = restartAskJob().withRoot(root).build(sessions, presence, relay, Clock.systemUTC());

    job.runOn(state.tasks());

    verify(relay)
        .say(
            "ABC-42",
            Step.RESTARTED,
            "jagt was restarted.",
            Standing.of(state.task("ABC-42").orElseThrow()));
  }

  @Test
  void asksNothingOfTaskWhoseTurnIsNotTheSessions() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    when(presence.agentLive("ABC-42")).thenReturn(true);

    job.runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void doesNotReviveSessionThatIsNotRunning() {
    when(presence.agentLive("ABC-42")).thenReturn(false);

    job.runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void doesNotTypeOverHumansDraft() {
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.composing("ABC-42")).thenReturn(true);

    job.runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void asksNothingOfSessionIdlePastItsCacheLifetime() {
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.cold("ABC-42")).thenReturn(true);

    job.runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void keepsAskingTheOtherTasksWhenOneCannotBeTold() throws IOException {
    Files.writeString(root.resolve("alive"), "2026-11-05T07:00:00Z");
    state.putTask("ABC-43", TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS).build());
    turns.opened("ABC-43", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive(anyString())).thenReturn(true);
    when(sessions.loggedSince(anyString(), eq(1793862000000L))).thenReturn(true);
    job = restartAskJob().withRoot(root).build(sessions, presence, relay, Clock.systemUTC());
    doThrow(new IllegalStateException("No session"))
        .when(relay)
        .say(
            "ABC-42",
            Step.RESTARTED,
            "jagt was restarted.",
            Standing.of(state.task("ABC-42").orElseThrow()));

    job.runOn(state.tasks());

    verify(relay)
        .say(
            "ABC-43",
            Step.RESTARTED,
            "jagt was restarted.",
            Standing.of(state.task("ABC-43").orElseThrow()));
  }

  @Test
  void asksNothingOfSessionThatWroteNothingWhileJagtWasDown() throws IOException {
    Files.writeString(root.resolve("alive"), "2026-11-05T07:00:00Z");
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.loggedSince("ABC-42", 1793862000000L)).thenReturn(false);
    job = restartAskJob().withRoot(root).build(sessions, presence, relay, Clock.systemUTC());

    job.runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void asksNobodyWhenJagtLeftNoAliveStamp() {
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.loggedSince(anyString(), anyLong())).thenReturn(true);

    job.runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void runsOnceAfterTheStartAndNotOnAnInterval() {
    assertThat(job.every()).isNull();
  }

  @Test
  void doesNotAskSessionWhoseWindowCouldNotBeRead() {
    when(presence.agentLive("ABC-42")).thenThrow(new AgentPresence.Unread("tmux timed out"));

    job.runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }
}
