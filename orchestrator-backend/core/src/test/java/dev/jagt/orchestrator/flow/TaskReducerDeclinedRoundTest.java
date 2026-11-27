package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.Turn;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskReducerDeclinedRoundTest {

  private final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});

  @Test
  void declinedRoundLandsDeployedAndRecordsTheDigestOnTheRole() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .deployment(new Deployment(5L, List.of(), 0, "d1"))
            .roles(Map.of("acceptance", new RoleRecord("", "", "d1", "- row", "")))
            .turn(new Turn("acceptance-round", 1L, "d1", 0L, Map.of(), List.of()))
            .build();

    TaskState declined =
        reducer.checkerDeclined(
            task, "acceptance", AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS, 10L);

    assertThat(declined.standing().status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(declined.work().roleOrNone("acceptance").declined()).isEqualTo("d1");
  }
}
