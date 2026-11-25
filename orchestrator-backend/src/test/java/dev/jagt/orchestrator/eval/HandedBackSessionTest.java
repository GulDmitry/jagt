package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandedBackSessionTest {

  @Test
  void recordOutlivesTheCliRemovingEmptyDirectoriesWhenNothingWasTyped(@TempDir Path root)
      throws Exception {
    Path record = root.resolve("-wt-ABC-42");

    HandedBackSession.record(record, List.of());

    assertThat(record.resolve(HandedBackSession.RECORD)).isEmptyFile();
  }

  @Test
  void worktreeHoldsTheFilesEveryHandedBackSessionLeaves(@TempDir Path worktree) throws Exception {
    HandedBackSession.files(worktree, "Correct the VAT rate.");

    assertThat(worktree.resolve("task_context.md")).hasContent("Correct the VAT rate.");
    assertThat(worktree.resolve("task_notes.md")).exists();
  }
}
