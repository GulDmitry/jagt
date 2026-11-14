package dev.jagt.orchestrator.service.plan;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.worktree.LineChange;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.task.TaskArtifacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PlanBaselineTest {

  @TempDir Path root;

  private Path worktree;
  private PlanBaseline baseline;

  @BeforeEach
  void keepsPlansOfTaskWithOneWorktree() throws Exception {
    worktree = Files.createDirectories(root.resolve("wt"));
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    StateService state =
        new StateService(
            new JsonMapper(),
            paths,
            new TaskReducer(new TaskChart(Composition.builtIn())),
            event -> {});
    state.putTask(
        "ABC-42", TaskState.builder("web", worktree.toString(), TaskStatus.PLAN_PENDING).build());
    baseline =
        new PlanBaseline(
            paths,
            new TaskArtifacts(state),
            (earlier, later) -> new LineChange(Set.of(), Map.of(0, List.of(earlier))));
  }

  @Test
  void marksNothingBeforeAnyPlanWasApproved() throws Exception {
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");

    LineChange change = baseline.since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).isEmpty();
  }

  @Test
  void comparesThePlanWithTheOneThatWasApproved() throws Exception {
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");
    baseline.keep("ABC-42");
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n- b\n");

    LineChange change = baseline.since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).containsEntry(0, List.of("# Plan\n- a\n"));
  }

  @Test
  void comparesWithTheLatestApprovalOnceTheChangedPlanIsApprovedAgain() throws Exception {
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");
    baseline.keep("ABC-42");
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n- b\n");
    baseline.keep("ABC-42");

    LineChange change = baseline.since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).containsEntry(0, List.of("# Plan\n- a\n- b\n"));
  }

  @Test
  void marksNothingWhenTheApprovalFoundNoPlanFileToKeep() throws Exception {
    baseline.keep("ABC-42");

    assertThat(baseline.since("ABC-42", "# Plan\n").removed()).isEmpty();
  }
}
