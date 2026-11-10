package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class LiftCapabilityTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void clearsTheStopAndTellsTheHumanWhatMovesAgain(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds"))
            .build());
    LiftCapability lift =
        new LiftCapability(
            new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(), new FlowRules(new TaskChart(Composition.builtIn()))));

    var outcome = lift.run("ABC-1");

    assertThat(lift.action()).isEqualTo(TaskAction.LIFT);
    assertThat(state.task("ABC-1").orElseThrow().stopped()).isFalse();
    assertThat(outcome.message())
        .isEqualTo(
            "lift ABC-1: auto-review and the Master may move it again; its rounds count anew");
  }
}
