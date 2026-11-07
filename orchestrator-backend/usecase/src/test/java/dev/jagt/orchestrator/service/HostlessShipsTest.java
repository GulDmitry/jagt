package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HostlessShipsTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final HostlessShips ships = new HostlessShips(configService, changes);
  private final ProjectConfig hostless =
      new ProjectConfig(
          new ProjectConfig.Repository("/repo", List.of(), null, null),
          new ProjectConfig.Branches("main", "dev", null),
          new ProjectConfig.CodeHost("none", null, null, null, null, null, null),
          new ProjectConfig.Verify(List.of(), null));
  private final ProjectConfig hosted = new ProjectConfig("/repo", "origin/main", "dev", List.of());

  @Test
  void standsWhereEveryRepositoryHasNoCodeHostAndNothingIsLeftUnpublished() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.REVIEW_PENDING)
            .build();
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("api", hostless, "web", hostless)));
    when(changes.anythingNewToShip(task)).thenReturn(false);

    assertThat(ships.stands(task)).isTrue();
  }

  @Test
  void doesNotStandWhereWorkIsLeftUnpublished() {
    TaskState task = TaskState.builder("api", "/wt-api", TaskStatus.REVIEW_PENDING).build();
    when(configService.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", hostless)));
    when(changes.anythingNewToShip(task)).thenReturn(true);

    assertThat(ships.stands(task)).isFalse();
  }

  @Test
  void doesNotStandWhereOneRepositoryHasACodeHost() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
                TaskStatus.REVIEW_PENDING)
            .build();
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("api", hosted, "web", hostless)));
    when(changes.anythingNewToShip(task)).thenReturn(false);

    assertThat(ships.stands(task)).isFalse();
  }

  @Test
  void doesNotStandOnceTheAgentWorksOnTheTaskAgain() {
    TaskState task = TaskState.builder("api", "/wt-api", TaskStatus.IN_PROGRESS).build();
    when(configService.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", hostless)));
    when(changes.anythingNewToShip(task)).thenReturn(false);

    assertThat(ships.stands(task)).isFalse();
  }

  @Test
  void doesNotStandWhereTheConfigurationNowGivesTheRepositoryACodeHost() {
    TaskState task = TaskState.builder("api", "/wt-api", TaskStatus.REVIEW_PENDING).build();
    when(configService.load())
        .thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("api", hosted)));
    when(changes.anythingNewToShip(task)).thenReturn(false);

    assertThat(ships.stands(task)).isFalse();
  }
}
