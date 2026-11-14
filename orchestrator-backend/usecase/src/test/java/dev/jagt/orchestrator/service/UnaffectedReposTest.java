package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UnaffectedReposTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final ConfigService config = mock(ConfigService.class);
  private final GitWorktrees git = mock(GitWorktrees.class);
  private final RepoDeparture departure = mock(RepoDeparture.class);

  @TempDir Path root;

  private final TaskRepo web = new TaskRepo("web", "/web-wt", "git@host:g/web.git", null);
  private StateService state;

  @BeforeEach
  void putsPlanPendingTaskOverTwoRepositories() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer,
            event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder(
                List.of(new TaskRepo("api", root.toString(), "git@host:g/api.git", null), web),
                TaskStatus.PLAN_PENDING)
            .build());
  }

  @Test
  void anUnaffectedRepositoryLeavesTheTaskWhereItsBranchHoldsNoOwnCommits() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        "## Repositories\n- [changed] api\n- [unaffected: only the API changes] web\n");
    when(config.project("web"))
        .thenReturn(new ProjectConfig("/srv/web", "origin/main", "dev", List.of()));
    when(git.holdsOwnCommits(Path.of("/srv/web"), "ABC-42", "origin/main")).thenReturn(false);

    new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

    verify(departure).leave("ABC-42", web, "the approved plan marks it unaffected");
  }

  @Test
  void thePrimaryRepositoryStaysOnTheTaskWhenThePlanMarksItUnaffected() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        "## Repositories\n- [unaffected: only the web changes] api\n- [changed] web\n");

    new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(departure);
  }

  @Test
  void anUnaffectedRepositoryHoldingOwnCommitsStaysOnTheTask() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        "## Repositories\n- [changed] api\n- [unaffected: only the API changes] web\n");
    when(config.project("web"))
        .thenReturn(new ProjectConfig("/srv/web", "origin/main", "dev", List.of()));
    when(git.holdsOwnCommits(Path.of("/srv/web"), "ABC-42", "origin/main")).thenReturn(true);

    new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(departure);
  }

  @Test
  void everyRepositoryStaysWhereThePlanMarksNoneUnaffected() throws IOException {
    Files.writeString(
        root.resolve("plan.md"), "## Repositories\n- [changed] api\n- [changed] web\n");

    new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(departure, git);
  }

  @Test
  void thePrimaryRepositoryAloneStaysWhereThePlanMarksEveryOneUnaffected() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        "## Repositories\n- [unaffected: nothing moves] api\n- [unaffected: nothing moves] web\n");
    when(config.project(any()))
        .thenReturn(new ProjectConfig("/srv/any", "origin/main", "dev", List.of()));

    new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

    verify(departure).leave(eq("ABC-42"), any(), any());
  }

  @Test
  void nothingLeavesWhereTheWorktreeHoldsNoPlan() {

    new UnaffectedRepos(state, new TaskArtifacts(state), config, git, departure).leave("ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
  }
}
