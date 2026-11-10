package dev.jagt.orchestrator.job;

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

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class RestartAskJobTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final AgentPresence presence = mock(AgentPresence.class);
  private final StepRelay relay = mock(StepRelay.class);

  private OrchestratorPaths paths(Path root) {
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString()));
  }

  private StateService state(Path root) {
    return new StateService(new JsonMapper(), paths(root), reducer, event -> {});
  }

  @Test
  void asksRunningSessionHoldingAnOpenTurnToRepeatItsReport(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("alive"), "2026-11-05T07:00:00Z");
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "round-not-ready", "digest", TaskStatus.REVIEW_PENDING);
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.loggedSince("ABC-42", 1793862000000L)).thenReturn(true);

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay)
        .say(
            "ABC-42",
            Step.RESTARTED,
            "jagt was restarted.",
            Standing.of(state.task("ABC-42").orElseThrow()));
  }

  @Test
  void asksNothingOfTaskWhoseTurnIsNotTheSessions(@TempDir Path root) {
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    when(presence.agentLive("ABC-42")).thenReturn(true);

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void doesNotReviveSessionThatIsNotRunning(@TempDir Path root) {
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive("ABC-42")).thenReturn(false);

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void doesNotTypeOverHumansDraft(@TempDir Path root) {
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.composing("ABC-42")).thenReturn(true);

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void asksNothingOfSessionIdlePastItsCacheLifetime(@TempDir Path root) {
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.cold("ABC-42")).thenReturn(true);

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void keepsAskingTheOtherTasksWhenOneCannotBeTold(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("alive"), "2026-11-05T07:00:00Z");
    StateService state = state(root);
    TaskTurns turns =
        new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())));
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-43", TaskState.builder("proj", "/wt2", TaskStatus.IN_PROGRESS).build());
    turns.opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
    turns.opened("ABC-43", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive(anyString())).thenReturn(true);
    when(sessions.loggedSince(anyString(), eq(1793862000000L))).thenReturn(true);
    doThrow(new IllegalStateException("No session"))
        .when(relay)
        .say(
            "ABC-42",
            Step.RESTARTED,
            "jagt was restarted.",
            Standing.of(state.task("ABC-42").orElseThrow()));

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay)
        .say(
            "ABC-43",
            Step.RESTARTED,
            "jagt was restarted.",
            Standing.of(state.task("ABC-43").orElseThrow()));
  }

  @Test
  void asksNothingOfSessionThatWroteNothingWhileJagtWasDown(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("alive"), "2026-11-05T07:00:00Z");
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.loggedSince("ABC-42", 1793862000000L)).thenReturn(false);

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void asksNobodyWhenJagtLeftNoAliveStamp(@TempDir Path root) {
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive("ABC-42")).thenReturn(true);
    when(sessions.loggedSince(anyString(), anyLong())).thenReturn(true);

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void runsOnceAfterTheStartAndNotOnAnInterval(@TempDir Path root) {
    assertThat(
            new RestartAskJob(
                    sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
                .every())
        .isNull();
  }

  @Test
  void doesNotAskSessionWhoseWindowCouldNotBeRead(@TempDir Path root) {
    StateService state = state(root);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())))
        .opened("ABC-42", "start", "digest", TaskStatus.IN_PROGRESS);
    when(presence.agentLive("ABC-42")).thenThrow(new AgentPresence.Unread("tmux timed out"));

    new RestartAskJob(sessions, presence, relay, new AliveStamp(paths(root), Clock.systemUTC()))
        .runOn(state.tasks());

    verify(relay, never()).say(anyString(), any(Step.class), anyString(), any(Standing.class));
  }
}
