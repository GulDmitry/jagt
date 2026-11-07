package dev.jagt.orchestrator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PlanApprovalLeavesTest {

  private final StepRelay relay = mock(StepRelay.class);
  private final CaseDoor door = mock(CaseDoor.class);
  private final UnaffectedRepos unaffected = mock(UnaffectedRepos.class);

  @Test
  void anApprovedPlanTakesItsUnaffectedRepositoriesOutOfTheTask(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build());
    when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
    PlanBaseline baseline =
        new PlanBaseline(paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none());

    new PlanApproval(
            state,
            relay,
            door,
            baseline,
            new ApprovedRepos(new PlanPlacement(state, null, null, null, null), unaffected))
        .approve("ABC-1", "The human read plan.md");

    verify(unaffected).leave("ABC-1");
  }

  @Test
  void aRefusedApprovalTakesNoRepositoryOutOfTheTask(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build());
    when(door.presses("ABC-1", "approve-plan")).thenReturn(false);
    PlanBaseline baseline =
        new PlanBaseline(paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none());

    new PlanApproval(
            state,
            relay,
            door,
            baseline,
            new ApprovedRepos(new PlanPlacement(state, null, null, null, null), unaffected))
        .approve("ABC-1", "The human read plan.md");

    verify(unaffected, never()).leave("ABC-1");
  }
}
