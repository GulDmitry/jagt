package dev.jagt.orchestrator.job.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HumanPathsTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();

  @Nested
  class WhereTheProjectKeepsPaths {

    @BeforeEach
    void theProjectKeepsAuthPathsForTheHuman() {
      when(configService.project("demo"))
          .thenReturn(
              new ProjectConfig(
                  new ProjectConfig.Repository(
                      "/repo", List.of(), null, List.of("**/auth/**"), null, null, null),
                  new ProjectConfig.Branches("origin/main", "dev", null),
                  ProjectConfig.CodeHost.defaults(),
                  new ProjectConfig.Verify(List.of(), null),
                  new ProjectConfig.Setup(List.of(), null, List.of(), List.of())));
    }

    @Test
    void namesTheFirstChangedPathTheProjectKeepsForTheHuman() {
      when(changes.paths(task))
          .thenReturn(Optional.of(Map.of("demo", List.of("README.md", "src/auth/Login.java"))));

      Optional<String> touched = new HumanPaths(configService, changes).touched(task);

      assertThat(touched).contains("demo/src/auth/Login.java");
    }

    @Test
    void namesNothingWhereNoChangedPathIsKept() {
      when(changes.paths(task)).thenReturn(Optional.of(Map.of("demo", List.of("README.md"))));

      Optional<String> touched = new HumanPaths(configService, changes).touched(task);

      assertThat(touched).isEmpty();
    }

    @Test
    void keepsTheStepWithTheHumanWhereTheChangedPathsCouldNotBeRead() {
      when(changes.paths(task)).thenReturn(Optional.empty());

      Optional<String> touched = new HumanPaths(configService, changes).touched(task);

      assertThat(touched).contains(HumanPaths.UNREAD);
    }
  }

  @Test
  void readsNoPathWhereTheProjectKeepsNone() {
    when(configService.project("demo"))
        .thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));

    Optional<String> touched = new HumanPaths(configService, changes).touched(task);

    assertThat(touched).isEmpty();
    verifyNoInteractions(changes);
  }

  @Test
  void keepsTheStepWithTheHumanWhereOneRepositoryOfTheTaskNamesNoProject() {
    TaskState unnamed =
        TaskState.builder(List.of(TaskRepo.of(null, "/wt")), TaskStatus.REVIEWED).build();

    Optional<String> touched = new HumanPaths(configService, changes).touched(unnamed);

    assertThat(touched).contains(HumanPaths.UNREAD);
  }

  @Test
  void keepsTheStepWithTheHumanWhereTheTaskNamesProjectNobodyConfigured() {
    when(configService.project("demo")).thenThrow(new IllegalArgumentException("no project demo"));

    Optional<String> touched = new HumanPaths(configService, changes).touched(task);

    assertThat(touched).contains(HumanPaths.UNREAD);
  }
}
