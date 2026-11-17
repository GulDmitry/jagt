package dev.jagt.orchestrator.service.worktree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.host.Processes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataFolderStartRepairTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final Processes runner = new ProcessRunner();

  @TempDir Path root;

  @Test
  void leavesTheWorktreeOfTaskInFlightUsableWhereItsCloneMoves() throws Exception {
    Path clone = root.resolve("repos/demo");
    Files.writeString(root.resolve("state.json"), "tasks");
    Files.createDirectories(clone.getParent());
    runner.run(root, TIMEOUT, List.of("git", "init", "-q", "-b", "main", clone.toString()));
    runner.run(
        clone,
        TIMEOUT,
        List.of(
            "git",
            "-c",
            "user.email=t@t",
            "-c",
            "user.name=t",
            "commit",
            "-q",
            "--allow-empty",
            "-m",
            "init"));
    Path worktree = root.resolve("beside/ABC-1");
    runner.run(
        clone,
        TIMEOUT,
        List.of("git", "worktree", "add", "-q", "-b", "ABC-1", worktree.toString()));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.resolve("vault").toString()));

    new DataFolderStart(paths, runner, "").prepare();

    assertThat(root.resolve("vault/projects/demo/.git")).isDirectory();
    assertThat(runner.run(worktree, TIMEOUT, List.of("git", "status")).exitCode()).isZero();
  }
}
