package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PlanBaselineTest {

  @TempDir Path root;

  private PlanBaseline baselineOver(Path worktree) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask(
        "ABC-42", TaskState.builder("web", worktree.toString(), TaskStatus.PLAN_PENDING).build());
    return new PlanBaseline(
        paths,
        new TaskArtifacts(state),
        (earlier, later) -> new LineChange(Set.of(), Map.of(0, List.of(earlier))));
  }

  @Test
  void marksNothingBeforeAnyPlanWasApproved() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");

    LineChange change = baselineOver(worktree).since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).isEmpty();
  }

  @Test
  void comparesThePlanWithTheOneThatWasApproved() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");
    PlanBaseline baseline = baselineOver(worktree);
    baseline.keep("ABC-42");
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n- b\n");

    LineChange change = baseline.since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).containsEntry(0, List.of("# Plan\n- a\n"));
  }

  @Test
  void comparesWithTheLatestApprovalOnceTheChangedPlanIsApprovedAgain() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");
    PlanBaseline baseline = baselineOver(worktree);
    baseline.keep("ABC-42");
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n- b\n");
    baseline.keep("ABC-42");

    LineChange change = baseline.since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).containsEntry(0, List.of("# Plan\n- a\n- b\n"));
  }

  @Test
  void marksNothingWhenTheApprovalFoundNoPlanFileToKeep() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    PlanBaseline baseline = baselineOver(worktree);
    baseline.keep("ABC-42");

    assertThat(baseline.since("ABC-42", "# Plan\n").removed()).isEmpty();
  }
}
