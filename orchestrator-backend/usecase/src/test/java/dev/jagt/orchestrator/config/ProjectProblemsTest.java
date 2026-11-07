package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectProblemsTest {

  @Test
  void refusesARepositoryWhoseParentJagtCannotCutWorktreesIn(@TempDir Path root) throws Exception {
    Path parent = Files.createDirectory(root.resolve("parent"));
    Path repo = Files.createDirectories(parent.resolve("repo").resolve(".git")).getParent();
    assertThat(parent.toFile().setWritable(false)).isTrue();

    assertThat(
            ProjectProblems.of(
                "demo", new ProjectConfig(repo.toString(), "origin/main", "dev", null)))
        .extracting(Problem::key, Problem::text)
        .containsExactly(
            tuple(
                "orchestrator.projects.demo.path",
                "projects.demo.path "
                    + repo
                    + ": jagt cuts every worktree next to the repository and cannot write in "
                    + parent
                    + "."));
  }
}
