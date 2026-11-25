package dev.jagt.orchestrator.service.repo;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.worktree.RetiredWorktrees;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoDepartureTest {

  private final ConfigService config = mock(ConfigService.class);
  private final RetiredWorktrees worktrees = mock(RetiredWorktrees.class);
  private final AgentSessions sessions = mock(AgentSessions.class);
  private final TaskRepo web =
      new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web");

  @TempDir Path root;

  private StateService state;
  private RepoDeparture departure;

  @BeforeEach
  void startsWithAnEmptyState() {
    state = stateService().withRoot(root).build();
    departure = new RepoDeparture(config, worktrees, sessions, state, new FakeClock());
  }

  @Test
  void takesSecondaryRepositoryOutOfTheTask() {
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    when(config.load()).thenReturn(ConfigFile.defaults());

    boolean left = departure.leave("ABC-42", web, "its request was closed on the host");

    assertThat(left).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
    assertThat(state.task("ABC-42").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.REVIEWED);
  }

  @Test
  void keepsThePrimaryRepositoryOfTaskHoldingOthers() {
    TaskRepo primary = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(primary, web), TaskStatus.REVIEWED).build());

    boolean left = departure.leave("ABC-42", primary, "its request was closed on the host");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(worktrees, sessions);
  }

  @Test
  void takesThePrimaryRepositoryHoldingNoWorkOutOfTaskHoldingOthersAndMovesTheHome() {
    TaskRepo primary = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(primary, web), TaskStatus.REVIEWED).build());
    when(config.load()).thenReturn(ConfigFile.defaults());
    when(sessions.windowState("ABC-42")).thenReturn(SessionHost.WindowState.DEAD_SHELL);

    boolean left = departure.leaveWorkless("ABC-42", primary, "it holds no work");

    assertThat(left).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().worktreePath()).isEqualTo("/web-wt");
    verify(worktrees).remove(eq("ABC-42"), eq(List.of(primary)), any());
  }

  @Test
  void keepsThePrimaryRepositoryWhileAnAgentRunsInIt() {
    TaskRepo primary = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(primary, web), TaskStatus.REVIEWED).build());
    when(sessions.windowState("ABC-42")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);

    boolean left = departure.leaveWorkless("ABC-42", primary, "it holds no work");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(worktrees);
  }

  @Test
  void keepsThePrimaryRepositoryWhereTheSessionsWindowCannotBeRead() {
    TaskRepo primary = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(primary, web), TaskStatus.REVIEWED).build());
    when(sessions.windowState("ABC-42")).thenThrow(new IllegalStateException("no tmux"));

    boolean left = departure.leaveWorkless("ABC-42", primary, "it holds no work");

    assertThat(left).isFalse();
  }

  @Test
  void keepsTheOnlyRepositoryThoughItHoldsNoWork() {
    TaskRepo only = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(only), TaskStatus.REVIEWED).build());

    boolean left = departure.leaveWorkless("ABC-42", only, "it holds no work");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
  }

  @Test
  void keepsRepositoryAlreadyDeployedUntilItIsReverted() {
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.DEPLOYED)
            .deployment(
                Deployment.defaults()
                    .withTargets(List.of(Deployment.Target.of("web", "dev").withMerge("abc123"))))
            .build());

    boolean left = departure.leave("ABC-42", web, "its request was closed on the host");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api", "web");
    verifyNoInteractions(worktrees, sessions);
  }

  @Test
  void takesRepositoryOutWhereTheDeployNeverLandedIt() {
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                Deployment.defaults().withTargets(List.of(Deployment.Target.of("web", "dev"))))
            .build());
    when(config.load()).thenReturn(ConfigFile.defaults());

    boolean left = departure.leave("ABC-42", web, "its request was closed on the host");

    assertThat(left).isTrue();
  }

  @Test
  void stampsTheTimeOfTheDepartureWhereTheConflictedRepositoryLeavesAndTheTaskLandsReviewed() {
    TaskState opened =
        state
            .reducer()
            .deployStepped(
                TaskState.builder(
                        List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.DEPLOY_CONFLICT)
                    .build(),
                new DeployStep.Opened(List.of(Deployment.Target.of("web", "dev"))),
                1_000);
    state.putTask(
        "ABC-42",
        state
            .reducer()
            .deployStepped(
                opened, new DeployStep.Conflicted("web", Deployment.Cause.MERGE), 1_100));
    when(config.load()).thenReturn(ConfigFile.defaults());

    departure.leave("ABC-42", web, "its request was closed on the host");

    assertThat(state.task("ABC-42").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.REVIEWED);
    assertThat(state.task("ABC-42").orElseThrow().standing().lastActiveTimestamp())
        .isEqualTo(1_792_749_600_000L);
    assertThat(state.task("ABC-42").orElseThrow().standing().history().getLast().at())
        .isEqualTo(1_792_749_600_000L);
  }

  @Test
  void keepsTheOnlyRepositoryOfTask() {
    TaskRepo only = TaskRepo.of("api", "/api-wt");
    state.putTask("ABC-42", TaskState.builder(List.of(only), TaskStatus.REVIEWED).build());

    boolean left = departure.leave("ABC-42", only, "you removed it");

    assertThat(left).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().projects()).containsExactly("api");
  }

  @Test
  void takesTheTrustedGitEntriesOnlyTheLeavingRepositoryHoldsWithIt() {
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
    when(config.load()).thenReturn(ConfigFile.defaults());
    when(worktrees.trustedOnlyBy(eq("ABC-42"), any(), eq(web)))
        .thenReturn(Set.of("core.bare in /web/.git/config"));

    departure.leave("ABC-42", web, "its request was closed on the host");

    assertThat(
            Objects.requireNonNull(state.task("ABC-42").orElseThrow().gate().gitTrust()).recorded())
        .containsOnlyKeys("core.bare in /api/.git/config");
  }

  @Test
  void removesTheCheckoutOfRepositoryThatLeftTheTask() {
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    Map<String, ProjectConfig> projects =
        Map.of("web", new ProjectConfig("/srv/web", "origin/main", "dev", List.of()));
    when(config.load()).thenReturn(ConfigFile.defaults().withProjects(projects));

    departure.leave("ABC-42", web, "its request was closed on the host");

    verify(worktrees).remove("ABC-42", List.of(web), projects);
  }

  @Test
  void tellsTheSessionWhichRepositoryLeftAndWhy() {
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    when(config.load()).thenReturn(ConfigFile.defaults());

    departure.leave("ABC-42", web, "its request was closed on the host");

    verify(sessions).relayUnder(eq("ABC-42"), contains("web"));
    verify(sessions).relayUnder(eq("ABC-42"), contains("its request was closed on the host"));
  }

  @Test
  void stillTellsTheSessionWhereTheCheckoutCouldNotBeRemoved() {
    state.putTask(
        "ABC-42",
        TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), web), TaskStatus.REVIEWED)
            .build());
    when(config.load()).thenReturn(ConfigFile.defaults());
    doThrow(new IllegalStateException("busy")).when(worktrees).remove(any(), any(), any());

    departure.leave("ABC-42", web, "its request was closed on the host");

    verify(sessions).relayUnder(eq("ABC-42"), contains("web"));
  }
}
