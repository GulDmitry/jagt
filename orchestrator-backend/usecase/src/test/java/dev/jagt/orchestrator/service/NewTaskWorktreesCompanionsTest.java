package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.git.GitWorktrees;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.repo.BranchStrategy;
import dev.jagt.orchestrator.task.repo.NewRepo;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class NewTaskWorktreesCompanionsTest {

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final GitWorktrees git = mock(GitWorktrees.class);
  private NewTaskWorktrees worktrees;
  private ConfigFile apiWithSpecs;

  @BeforeEach
  void setUp() {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.resolve("data").toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine());
    StateService state =
        new StateService(
            new JsonMapper(),
            paths,
            new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}),
            event -> {});
    worktrees = new NewTaskWorktrees(config, state, git, mock(WorktreeSetup.class), paths);
    apiWithSpecs =
        ConfigFile.defaults()
            .withProjects(
                new LinkedHashMap<>(
                    Map.of(
                        "api",
                        TestProjectConfigBuilder.projectConfig()
                            .withPath(root.resolve("api-repo").toString())
                            .withSetup(
                                new ProjectConfig.Setup(
                                    List.of(), null, List.of(), List.of("specs")))
                            .build(),
                        "specs",
                        TestProjectConfigBuilder.projectConfig()
                            .withPath(root.resolve("specs-repo").toString())
                            .build())));
  }

  @Test
  void cutsTheCompanionOfTheStartedProjectBesideIt() {

    List<NewRepo> repos =
        worktrees.cut(NewTask.builder("ABC-7", "api").build(), apiWithSpecs, BranchStrategy.FRESH);

    assertThat(repos)
        .extracting(NewRepo::project, NewRepo::primary)
        .containsExactly(tuple("api", true), tuple("specs", false));
    verify(git)
        .createWorktree(
            root.resolve("specs-repo"),
            root.resolve("data/tasks/ABC-7/worktrees/specs"),
            "ABC-7",
            "origin/main",
            BranchStrategy.FRESH);
  }

  @Test
  void placesTheCompanionOfThePlannedProjectBesideIt() {
    when(config.load()).thenReturn(apiWithSpecs);

    List<NewRepo> placed =
        worktrees.place(NewTask.builder("ABC-9", "api").build(), apiWithSpecs, repos -> {});

    assertThat(placed).extracting(NewRepo::project).containsExactly("api", "specs");
  }
}
