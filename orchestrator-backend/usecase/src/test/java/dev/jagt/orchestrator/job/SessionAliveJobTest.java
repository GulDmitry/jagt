package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.StopPolicy;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SessionAliveJobTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()));

  private final AgentPresence agents = mock(AgentPresence.class);
  private final Notifications notifications = mock(Notifications.class);

  @Test
  void endsAStopForAStepThatNeverLandedOnceTheSessionsWindowRunsAgain(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC), new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", TaskStop.Trigger.UNDELIVERED, "the step could not be told to the session");
    when(agents.agentLive("ABC-42")).thenReturn(true);

    new SessionAliveJob(agents, stops).runOn(state.tasks());

    assertThat(stops.stopped("ABC-42")).isFalse();
  }

  @Test
  void keepsAStopForAStepThatNeverLandedWhileTheSessionsWindowIsNotRunning(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC), new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", TaskStop.Trigger.UNDELIVERED, "the step could not be told to the session");
    when(agents.agentLive("ABC-42")).thenReturn(false);

    new SessionAliveJob(agents, stops).runOn(state.tasks());

    assertThat(stops.stopped("ABC-42")).isTrue();
  }

  @Test
  void keepsAStopForAStepThatNeverLandedWhileTheSessionsWindowCannotBeRead(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC), new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", TaskStop.Trigger.UNDELIVERED, "the step could not be told to the session");
    when(agents.agentLive("ABC-42")).thenThrow(new AgentPresence.Unread("tmux timed out"));

    new SessionAliveJob(agents, stops).runOn(state.tasks());

    assertThat(stops.stopped("ABC-42")).isTrue();
  }
}
