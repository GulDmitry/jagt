package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.git.GitWorktrees;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TaskRetargetTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});

  @TempDir Path root;

  private final GitWorktrees git = mock(GitWorktrees.class);
  private final ConfigService config = mock(ConfigService.class);
  private StateService state;
  private TaskRetarget retarget;

  @BeforeEach
  void putsInProgressTaskOnOriginProject() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()),
            reducer,
            event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(config.project("proj"))
        .thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
    retarget = new TaskRetarget(state, git, config);
  }

  @Test
  void recordsTheNewBaseOnceOriginHasItAndTheWorktreeStandsOnIt() {
    when(git.remoteBranchExists(Path.of("/wt"), "ABC-41")).thenReturn(true);
    when(git.standsOn(Path.of("/wt"), "ABC-41")).thenReturn(true);

    retarget.retarget("ABC-42", "origin/ABC-41");

    assertThat(state.task("ABC-42").orElseThrow().ticket().baseBranch()).isEqualTo("ABC-41");
  }

  @Test
  void keepsTheBaseWhenTheWorktreeDoesNotStandOnTheBranchItNames() {
    when(git.remoteBranchExists(Path.of("/wt"), "ABC-41")).thenReturn(true);

    assertThatThrownBy(() -> retarget.retarget("ABC-42", "ABC-41")).isInstanceOf(Refusal.class);
    assertThat(state.task("ABC-42").orElseThrow().ticket().baseBranch()).isNull();
  }

  @Test
  void keepsTheBaseWhenOriginHasNoSuchBranch() {
    when(git.standsOn(Path.of("/wt"), "ABC-41")).thenReturn(true);

    assertThatThrownBy(() -> retarget.retarget("ABC-42", "ABC-41")).isInstanceOf(Refusal.class);
    assertThat(state.task("ABC-42").orElseThrow().ticket().baseBranch()).isNull();
  }

  @Test
  void takesTheRemoteOfTheProjectOffTheBranchItIsGiven() {
    when(config.project("proj"))
        .thenReturn(
            TestProjectConfigBuilder.projectConfig()
                .withBaseBranch("upstream/main")
                .withRemote("upstream")
                .build());
    when(git.remoteBranchExists(Path.of("/wt"), "ABC-41")).thenReturn(true);
    when(git.standsOn(Path.of("/wt"), "ABC-41")).thenReturn(true);

    retarget.retarget("ABC-42", "upstream/ABC-41");

    assertThat(state.task("ABC-42").orElseThrow().ticket().baseBranch()).isEqualTo("ABC-41");
  }
}
