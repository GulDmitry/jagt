package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
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

class PauseCapabilityTest {

  @Test
  void stopsTheTaskOnPauseAndToldNothingMovesUntilTheLift(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    PauseCapability pause =
        new PauseCapability(
            new TaskStops(
                ignored -> Arbiter.Profile.OFF,
                state,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(
                    new Notifications(List.of(), state, Runnable::run),
                    new StopLog(new JsonMapper(), paths)),
                Clock.systemUTC()));

    var outcome = pause.run("ABC-1");

    assertThat(pause.action()).isEqualTo(TaskAction.PAUSE);
    assertThat(state.task("ABC-1").orElseThrow().stop().trigger())
        .isEqualTo(TaskStop.Trigger.PAUSE);
    assertThat(outcome.message())
        .isEqualTo("pause ABC-1: nothing of jagt moves it until you lift the stop");
  }
}
