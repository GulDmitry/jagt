package dev.jagt.orchestrator.service.presence;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeepAliveTest {

  @TempDir Path root;

  @Test
  void dropsTheBeatOfTheCallTakenBeforeTheSessionStartedOver() {
    StateService state = stateService().withRoot(root).build();
    TaskState held =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).lastActiveTimestamp(1_000).build();
    state.putTask("ABC-1", held);
    state.updateTask(
        "ABC-1",
        new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {})
            ::sessionStartedOver);

    KeepAlive.beat(state, Map.entry("ABC-1", held), new SessionSign.Called(), 100_000);

    assertThat(state.task("ABC-1").orElseThrow().standing().lastActiveTimestamp()).isEqualTo(1_000);
  }
}
