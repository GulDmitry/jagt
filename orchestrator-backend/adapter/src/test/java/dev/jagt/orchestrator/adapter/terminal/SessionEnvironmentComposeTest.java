package dev.jagt.orchestrator.adapter.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.worktree.DockerComposeContainers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionEnvironmentComposeTest {

  private final StateService state = mock(StateService.class);
  private final DockerComposeContainers containers =
      new DockerComposeContainers(mock(Processes.class));
  private final SessionEnvironment environment =
      new SessionEnvironment(
          new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine()),
          containers,
          FakeHost.machine(),
          state);

  @ParameterizedTest
  @CsvSource({"b,b-abc-42", "My Repo.2,my-repo-2-abc-42"})
  void composeProjectIsTheRepositorysOfTheWorktreeCutAfterTheSessionStartedInAnotherOne(
      String repository, String expected, @TempDir Path root) throws Exception {
    Path first = Files.createDirectories(root.resolve("ABC-42/worktrees/a"));
    when(state.findByWorktree(first.toString())).thenReturn(Optional.empty());
    containers.writeStackName(first);
    String prefix = environment.prefix(first);
    Path cut = Files.createDirectories(root.resolve("ABC-42/worktrees").resolve(repository));
    containers.writeStackName(cut);

    String project = new FakeComposeSession(root).composeProject(prefix, cut);

    assertThat(project).isEqualTo(expected);
  }

  @Test
  void composeProjectIsTheSessionsOwnInFolderNoWorktreeNamesOne(@TempDir Path root)
      throws Exception {
    Path first = Files.createDirectories(root.resolve("ABC-42/worktrees/a"));
    when(state.findByWorktree(first.toString())).thenReturn(Optional.empty());
    containers.writeStackName(first);
    Path elsewhere = Files.createDirectories(root.resolve("elsewhere"));

    String project =
        new FakeComposeSession(root).composeProject(environment.prefix(first), elsewhere);

    assertThat(project).isEqualTo("a-abc-42");
  }
}
