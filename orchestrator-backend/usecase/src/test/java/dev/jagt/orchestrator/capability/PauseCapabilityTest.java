package dev.jagt.orchestrator.capability;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PauseCapabilityTest {

  @Test
  void stopsTheTaskOnPauseAndToldNothingMovesUntilTheLift(@TempDir Path root) {
    StateService state = stateService().withRoot(root).build();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    PauseCapability pause =
        new PauseCapability(taskStops().withRoot(root).build(state, Clock.systemUTC()));

    var outcome = pause.run("ABC-1");

    assertThat(pause.action()).isEqualTo(TaskAction.PAUSE);
    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().stop()).trigger())
        .isEqualTo(TaskStop.Trigger.PAUSE);
    assertThat(outcome.message())
        .isEqualTo("pause ABC-1: nothing of jagt moves it until you lift the stop");
  }
}
