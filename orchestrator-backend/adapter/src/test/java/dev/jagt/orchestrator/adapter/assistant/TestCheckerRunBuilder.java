package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.assistant.CheckerReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.With;

@With
public record TestCheckerRunBuilder(
    String prompt, Path worktree, CheckerReader.Reach reach, String schema, String role) {

  public static TestCheckerRunBuilder checkerRun() {
    return new TestCheckerRunBuilder(
        "check ABC-42",
        Path.of("/wt"),
        new CheckerReader.Reach(List.of(), List.of(), Optional.empty()),
        "{}",
        "acceptance");
  }

  public CheckerReader.Run build() {
    return new CheckerReader.Run("", prompt, worktree, "abc123", "", reach, schema, role);
  }
}
