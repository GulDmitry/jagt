package dev.jagt.orchestrator.job.session;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.log.StopLog;
import dev.jagt.orchestrator.service.stop.StopAnnouncements;
import dev.jagt.orchestrator.service.stop.StopPolicy;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SessionAliveJobTest {

  @TempDir Path root;
  private final AgentPresence agents = mock(AgentPresence.class);
  private StateService state;
  private TaskStops stops;

  @BeforeEach
  void taskStoppedBecauseItsStepNeverLanded() {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    state = stateService().withRoot(root).build();
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(new StopLog(new JsonMapper(), paths))),
            state,
            Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC),
            new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", TaskStop.Trigger.UNDELIVERED, "the step could not be told to the session");
  }

  @Test
  void endsStopForStepThatNeverLandedOnceTheSessionsWindowRunsAgain() {
    when(agents.agentLive("ABC-42")).thenReturn(true);

    new SessionAliveJob(agents, stops).runOn(state.tasks());

    assertThat(stops.stopped("ABC-42")).isFalse();
  }

  @Test
  void keepsStopForStepThatNeverLandedWhileTheSessionsWindowIsNotRunning() {
    when(agents.agentLive("ABC-42")).thenReturn(false);

    new SessionAliveJob(agents, stops).runOn(state.tasks());

    assertThat(stops.stopped("ABC-42")).isTrue();
  }

  @Test
  void keepsStopForStepThatNeverLandedWhileTheSessionsWindowCannotBeRead() {
    when(agents.agentLive("ABC-42")).thenThrow(new AgentPresence.Unread("tmux timed out"));

    new SessionAliveJob(agents, stops).runOn(state.tasks());

    assertThat(stops.stopped("ABC-42")).isTrue();
  }
}
