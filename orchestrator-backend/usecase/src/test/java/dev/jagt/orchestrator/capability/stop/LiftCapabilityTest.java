package dev.jagt.orchestrator.capability.stop;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiftCapabilityTest {

  @Test
  void clearsTheStopAndTellsTheHumanWhatMovesAgain(@TempDir Path root) {
    StateService state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds"))
            .build());
    LiftCapability lift =
        new LiftCapability(taskStops().withRoot(root).build(state, new FakeClock()));

    var outcome = lift.run("ABC-1");

    assertThat(lift.action()).isEqualTo(TaskAction.LIFT);
    assertThat(state.task("ABC-1").orElseThrow().stopped()).isFalse();
    assertThat(outcome.message())
        .isEqualTo(
            "lift ABC-1: auto-review and the arbiter may move it again; its rounds count anew");
  }
}
