package dev.jagt.orchestrator.adapter.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.host.Processes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
  void namesProjectAfterRepositoryAndTaskInLowerCase() {
    Map<String, String> environment =
        new DockerComposeContainers(mock(Processes.class))
            .environment(Path.of("/data/tasks/ABC-42/worktrees/Shop.Web"));

    assertThat(environment).containsExactly(Map.entry("COMPOSE_PROJECT_NAME", "shop-web-abc-42"));
  }

  @Test
  void namesTwoTasksOfOneRepositoryApart() {
    DockerComposeContainers containers = new DockerComposeContainers(mock(Processes.class));

    assertThat(containers.environment(Path.of("/data/tasks/ABC-1/worktrees/shop")))
        .isNotEqualTo(containers.environment(Path.of("/data/tasks/ABC-2/worktrees/shop")));
  }

  @Test
  void takesDownTheTasksOwnProjectEvenWhenComposeListsNone(@TempDir Path root) throws Exception {
    Path worktree = Files.createDirectories(root.toRealPath().resolve("ABC-42/worktrees/shop"));
    Processes runner = mock(Processes.class);
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));
    String docker = Executables.resolve("docker");

    new DockerComposeContainers(runner).stop(worktree);

    verify(runner)
        .run(
            isNull(),
            any(Duration.class),
            eq(List.of(docker, "compose", "-p", "shop-abc-42", "down", "-v", "--remove-orphans")));
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
