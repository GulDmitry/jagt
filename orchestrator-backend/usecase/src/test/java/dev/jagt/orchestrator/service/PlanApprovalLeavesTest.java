package dev.jagt.orchestrator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PlanApprovalLeavesTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final StepRelay relay = mock(StepRelay.class);
  private final CaseDoor door = mock(CaseDoor.class);
  private final ApprovedPlan plan = mock(ApprovedPlan.class);

  @TempDir Path root;

  private StateService state;

  @BeforeEach
  void putsPlanPendingTaskSpanningTwoRepositories() {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build());
  }

  @Test
  void anApprovedPlanTakesItsUnaffectedRepositoriesOutOfTheTask() {
    when(door.presses("ABC-1", "approve-plan")).thenReturn(true);

    new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md");

    verify(plan).leave("ABC-1");
  }

  @Test
  void refusedApprovalTakesNoRepositoryOutOfTheTask() {
    when(door.presses("ABC-1", "approve-plan")).thenReturn(false);

    new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md");

    verify(plan, never()).leave("ABC-1");
  }
}
