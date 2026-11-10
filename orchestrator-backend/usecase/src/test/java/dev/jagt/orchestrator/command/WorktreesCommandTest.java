package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.WorktreeOrphans;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorktreesCommandTest {

  @Test
  void listsEachOrphanWithItsSecretsAndTheVerbThatDeletesIt() {
    String report =
        WorktreesCommand.render(
            List.of(
                new WorktreeOrphans.Orphan(Path.of("/w/ABC-40-demo"), "demo", 2),
                new WorktreeOrphans.Orphan(Path.of("/w/ABC-41-demo"), "demo", 0)));

    assertThat(report)
        .contains(
            "ABC-40-demo  ",
            " 2 copied secret file(s)   delete-worktree ABC-40-demo",
            " 0 copied secret file(s)   delete-worktree ABC-41-demo");
  }

  @Test
  void saysNoWorktreeIsOrphanedWhereThereIsNone() {
    assertThat(WorktreesCommand.render(List.of()))
        .isEqualTo("no orphaned worktree: every directory jagt cut belongs to a task.");
  }
}
