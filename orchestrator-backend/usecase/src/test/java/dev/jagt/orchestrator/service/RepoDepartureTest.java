package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class RepoDepartureTest {

  private final ConfigService config = mock(ConfigService.class);
  private final RetiredWorktrees worktrees = mock(RetiredWorktrees.class);
  private final AgentSessions sessions = mock(AgentSessions.class);
  private final TaskRepo web =
      new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");

  private StateService stateOf(Path root) {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString())));
  }

  @Test
  void takesASecondaryRepositoryOutOfTheTask(@TempDir Path root) {
    StateService state = stateOf(root);
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

    boolean left =
        new RepoDeparture(config, worktrees, sessions, state)
            .leave("ABC-42", web, "its request was closed on the host");

    assertThat(left).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
    assertThat(state.task("ABC-42").orElseThrow().status()).isEqualTo(TaskStatus.REVIEWED);
  }

  @Test
  void keepsThePrimaryRepositoryOfATaskHoldingOthers(@TempDir Path root) {
    StateService state = stateOf(root);
    TaskRepo primary = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(primary, web), TaskStatus.REVIEWED).build());

    boolean left =
        new RepoDeparture(config, worktrees, sessions, state)
            .leave("ABC-42", primary, "its request was closed on the host");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(worktrees, sessions);
  }

  @Test
  void keepsARepositoryAlreadyDeployedUntilItIsReverted(@TempDir Path root) {
    StateService state = stateOf(root);
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.DEPLOYED)
            .deployment(
                Deployment.defaults()
                    .withTargets(
                        List.of(Deployment.Target.of("web", "dev").withMerge("abc123"))))
            .build());

    boolean left =
        new RepoDeparture(config, worktrees, sessions, state)
            .leave("ABC-42", web, "its request was closed on the host");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(worktrees, sessions);
  }

  @Test
  void takesARepositoryOutWhereTheDeployNeverLandedIt(@TempDir Path root) {
    StateService state = stateOf(root);
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                Deployment.defaults().withTargets(List.of(Deployment.Target.of("web", "dev"))))
            .build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

    boolean left =
        new RepoDeparture(config, worktrees, sessions, state)
            .leave("ABC-42", web, "its request was closed on the host");

    assertThat(left).isTrue();
  }

  @Test
  void keepsTheOnlyRepositoryOfATask(@TempDir Path root) {
    StateService state = stateOf(root);
    TaskRepo only = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(only), TaskStatus.REVIEWED).build());

    boolean left =
        new RepoDeparture(config, worktrees, sessions, state)
            .leave("ABC-42", only, "you removed it");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
  }

  @Test
  void takesTheTrustedGitEntriesOnlyTheLeavingRepositoryHoldsWithIt(@TempDir Path root) {
    StateService state = stateOf(root);
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .gitTrust(
                GitTrust.of(
                    Map.of(
                        "core.bare in /api/.git/config",
                        "a1",
                        "core.bare in /web/.git/config",
                        "b2")))
            .build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(worktrees.trustedOnlyBy(eq("ABC-42"), any(), eq(web)))
        .thenReturn(Set.of("core.bare in /web/.git/config"));

    new RepoDeparture(config, worktrees, sessions, state)
        .leave("ABC-42", web, "its request was closed on the host");

    assertThat(state.task("ABC-42").orElseThrow().gitTrust().recorded())
        .containsOnlyKeys("core.bare in /api/.git/config");
  }

  @Test
  void removesTheCheckoutOfARepositoryThatLeftTheTask(@TempDir Path root) {
    StateService state = stateOf(root);
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    Map<String, ProjectConfig> projects =
        Map.of("web", new ProjectConfig("/srv/web", "origin/main", "dev", List.of()));
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(projects));

    new RepoDeparture(config, worktrees, sessions, state)
        .leave("ABC-42", web, "its request was closed on the host");

    verify(worktrees).remove("ABC-42", List.of(web), projects);
  }

  @Test
  void tellsTheSessionWhichRepositoryLeftAndWhy(@TempDir Path root) {
    StateService state = stateOf(root);
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

    new RepoDeparture(config, worktrees, sessions, state)
        .leave("ABC-42", web, "its request was closed on the host");

    verify(sessions).relayUnder(eq("ABC-42"), contains("web"), anyBoolean());
    verify(sessions)
        .relayUnder(eq("ABC-42"), contains("its request was closed on the host"), anyBoolean());
  }

  @Test
  void stillTellsTheSessionWhereTheCheckoutCouldNotBeRemoved(@TempDir Path root) {
    StateService state = stateOf(root);
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    doThrow(new IllegalStateException("busy")).when(worktrees).remove(any(), any(), any());

    new RepoDeparture(config, worktrees, sessions, state)
        .leave("ABC-42", web, "its request was closed on the host");

    verify(sessions).relayUnder(eq("ABC-42"), contains("web"), anyBoolean());
  }
}
