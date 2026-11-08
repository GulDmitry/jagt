package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Processes;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DockerComposeContainersTest {

  @Test
  void selectsOnlyProjectsWhoseComposeFileLiesUnderTheWorktree() {
    String json =
        """
        [{"Name":"alpha","ConfigFiles":"/work/wt/app/compose.yml,/work/other/o.yml"},
         {"Name":"beta","ConfigFiles":"/work/other/compose.yml"},
         {"Name":"gamma","ConfigFiles":"/work/wt-sibling/compose.yml"}]
        """;

    List<String> projects =
        DockerComposeContainers.projectsUnder(json, List.of(Path.of("/work/wt")));

    assertThat(projects).containsExactly("alpha");
  }

  @Test
  void takesDownTheProjectInsideTheWorktreeAndNeverOneOutside(@TempDir Path worktree)
      throws Exception {
    Path real = worktree.toRealPath();
    String json =
        "[{\"Name\":\"alpha\",\"ConfigFiles\":\""
            + real
            + "/compose.yml\"},{\"Name\":\"beta\",\"ConfigFiles\":\"/elsewhere/compose.yml\"}]";
    Processes runner = mock(Processes.class);
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, json, ""));
    String docker = Executables.resolve("docker");

    new DockerComposeContainers(runner).stop(real);

    verify(runner)
        .run(
            isNull(),
            any(Duration.class),
            eq(List.of(docker, "compose", "-p", "alpha", "down", "-v", "--remove-orphans")));
    verify(runner, never())
        .run(
            isNull(),
            any(Duration.class),
            eq(List.of(docker, "compose", "-p", "beta", "down", "-v", "--remove-orphans")));
  }

  @Test
  void stopsNothingAndThrowsNothingWhenDockerIsNotInstalled(@TempDir Path worktree) {
    Processes runner = mock(Processes.class);
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenThrow(new IllegalStateException("Failed to start command", new IOException()));

    new DockerComposeContainers(runner).stop(worktree);

    verify(runner).run(isNull(), any(Duration.class), anyList());
  }
}
