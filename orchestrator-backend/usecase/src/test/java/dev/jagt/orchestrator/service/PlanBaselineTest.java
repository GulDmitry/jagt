package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestPlanBaselineBuilder.planBaseline;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.LineChange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanBaselineTest {

  @TempDir Path root;

  @Test
  void marksNothingBeforeAnyPlanWasApproved() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");

    LineChange change =
        planBaseline()
            .withRoot(root)
            .withWorktree(worktree)
            .build()
            .since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).isEmpty();
  }

  @Test
  void comparesThePlanWithTheOneThatWasApproved() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");
    PlanBaseline baseline = planBaseline().withRoot(root).withWorktree(worktree).build();
    baseline.keep("ABC-42");
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n- b\n");

    LineChange change = baseline.since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).containsEntry(0, List.of("# Plan\n- a\n"));
  }

  @Test
  void comparesWithTheLatestApprovalOnceTheChangedPlanIsApprovedAgain() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n");
    PlanBaseline baseline = planBaseline().withRoot(root).withWorktree(worktree).build();
    baseline.keep("ABC-42");
    Files.writeString(worktree.resolve("plan.md"), "# Plan\n- a\n- b\n");
    baseline.keep("ABC-42");

    LineChange change = baseline.since("ABC-42", "# Plan\n- a\n- b\n");

    assertThat(change.removed()).containsEntry(0, List.of("# Plan\n- a\n- b\n"));
  }

  @Test
  void marksNothingWhenTheApprovalFoundNoPlanFileToKeep() throws Exception {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    PlanBaseline baseline = planBaseline().withRoot(root).withWorktree(worktree).build();
    baseline.keep("ABC-42");

    assertThat(baseline.since("ABC-42", "# Plan\n").removed()).isEmpty();
  }
}
