package dev.jagt.orchestrator.capability.deploy;

import static dev.jagt.orchestrator.capability.deploy.TestDeployProjectsBuilder.deployProjects;
import static dev.jagt.orchestrator.capability.deploy.TestDeployTaskBuilder.apiAndWebTask;
import static dev.jagt.orchestrator.capability.deploy.TestDeployTaskBuilder.projectTask;
import static dev.jagt.orchestrator.capability.deploy.TestDeploymentBuilder.apiMergedAndWebConflicted;
import static dev.jagt.orchestrator.capability.deploy.TestDeploymentBuilder.deployment;
import static dev.jagt.orchestrator.capability.deploy.TestDeploymentBuilder.projectMergedAndConflictedBy;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeployServiceTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final GitDeploy git = mock(GitDeploy.class);
  private ConfigService.ConfigFile projects = deployProjects().build();
  private StateService state;
  private DeployService deploys;

  @BeforeEach
  void everyProjectDeploysOntoDev() {
    state = stateService().withRoot(root).build();
    when(config.load()).thenAnswer(call -> projects);
    when(config.project(anyString()))
        .thenAnswer(call -> projects.projects().get(call.getArgument(0, String.class)));
    DeployTargets targets = new DeployTargets(config);
    deploys =
        new DeployService(
            state, targets, git, new DeployRunner(state, targets, git, Clock.systemUTC()));
  }

  @Test
  void refusesTaskThatIsNotThereNamingIt() {
    assertThatThrownBy(() -> deploys.deploy("ABC-9"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("No task ABC-9")
        .extracting("code")
        .isEqualTo(Refusal.Code.NO_SUCH_TASK);
  }

  @Nested
  class OneProject {

    @BeforeEach
    void taskWaitsOnItsPipeline() {
      state.putTask(
          "ABC-1",
          projectTask().withStatus(TaskStatus.CI_POLLING).withMessage("MR: http://x").build());
    }

    @Test
    void reportsFullDeployAsDoneAndStampsTheBranchItLandedOn() {
      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.stamp()).isEqualTo("deployed to dev");
    }

    @Test
    void blockedDeployWithNothingLandedNamesTheObstacleInsteadOfAdvisingTheSameRunAgain() {
      when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev")))
          .thenThrow(
              new GitDeploy.ForeignDeployWorktreeException(
                  Path.of("/repos/ABC-1-deploy"), Path.of("/repos/x")));

      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.message())
          .contains("/repos/ABC-1-deploy")
          .doesNotContain("Run `deploy ABC-1` again");
    }

    @Test
    void recordsTheMergeCommitTheDeployCreatedSoItCanBeReverted() {
      when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");

      Outcome outcome = deploys.deploy("a1");

      assertThat(
              Objects.requireNonNull(state.task("ABC-1").orElseThrow().deployment())
                  .targets()
                  .getFirst()
                  .landing()
                  .merge())
          .isEqualTo("cafebabe1234");
      assertThat(outcome.message()).contains("cafebabe");
    }

    @Test
    void recordsTheLandingOnTheDeploymentBesideTheMergeCommitField() {
      when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");

      deploys.deploy("a1");

      Deployment deployment = state.task("ABC-1").orElseThrow().deployment();
      assertThat(Objects.requireNonNull(deployment).at()).isEqualTo(1);
      assertThat(deployment.targets().getFirst().landing().merge()).isEqualTo("cafebabe1234");
      assertThat(deployment.targets().getFirst().deployBranch()).isEqualTo("dev");
    }

    @Test
    void handsBackConflictWithoutOpeningAnEditorOrTouchingTheTaskBranch() throws Exception {
      Path worktree = Files.createDirectories(root.resolve("wt"));
      state.putTask(
          "ABC-2",
          TaskState.builder("proj", worktree.toString(), TaskStatus.CI_POLLING)
              .message("MR: http://x")
              .alias("a2")
              .build());
      Path deployWorktree = root.resolve("ABC-2-deploy");
      doThrow(
              new GitDeploy.MergeConflictException(
                  "ABC-2", "dev", "conflict in schema.yaml", deployWorktree))
          .when(git)
          .mergeIntoAndPush(any(), eq("ABC-2"), eq("dev"));

      Outcome outcome = deploys.deploy("a2");

      assertThat(worktree.resolve("task_context.md")).doesNotExist();
      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
      assertThat(outcome.message())
          .contains(deployWorktree.toString())
          .contains("nothing pushed")
          .contains("deploy ABC-2")
          .doesNotContain("Live on the deploy branch");
      assertThat(outcome.stamp()).isEqualTo("resolve conflict in " + deployWorktree);
    }

    @Test
    void refusesToLandTaskOntoTheVeryBranchItWasCutFrom() {
      when(config.project("proj"))
          .thenReturn(new ProjectConfig("/repo", "origin/release", "release", List.of()));

      assertThatThrownBy(() -> deploys.deploy("a1"))
          .isInstanceOf(Refusal.class)
          .hasMessageContaining("base branch")
          .extracting("code")
          .isEqualTo(Refusal.Code.STATE);
      verifyNoInteractions(git);
    }

    @Test
    void refusesToGuessWhereToLandProjectThatNamesNoDeployBranch() {
      projects = deployProjects().withProjDeployBranch(null).build();

      assertThatThrownBy(() -> deploys.deploy("ABC-1"))
          .isInstanceOf(Refusal.class)
          .hasMessageContaining("deployBranch")
          .extracting("code")
          .isEqualTo(Refusal.Code.STATE);
    }
  }

  @Nested
  class OneProjectInConflict {

    @BeforeEach
    void theDeployWorktreeStandsAndTheFixIsPending() {
      when(git.hasDeployWorktree(Path.of("/repo"), "ABC-1")).thenReturn(true);
    }

    @Test
    void recordsFixPushedAfterTheMergeAsTheTipAndKeepsTheMergeForRevert() {
      state.putTask(
          "ABC-1",
          projectTask()
              .withStatus(TaskStatus.DEPLOY_CONFLICT)
              .withDeployment(projectMergedAndConflictedBy(Deployment.Cause.MERGE).build())
              .build());
      when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("f1xf1x5678");
      when(git.isFix(any(), eq("f1xf1x5678"))).thenReturn(true);

      deploys.deploy("a1");

      Deployment.Target landed =
          Objects.requireNonNull(state.task("ABC-1").orElseThrow().deployment())
              .targets()
              .getFirst();
      assertThat(landed.landing().merge()).isEqualTo("cafebabe1234");
      assertThat(landed.tip()).isEqualTo("f1xf1x5678");
    }

    @Test
    void refusesPressWhileTheDeployWaitsOnTheSessionsFixAndLeavesTheStatusAlone() {
      state.putTask(
          "ABC-1",
          projectTask()
              .withStatus(TaskStatus.DEPLOY_CONFLICT)
              .withMessage("red pipeline on the deploy commit: proj cafebabe")
              .withDeployment(projectMergedAndConflictedBy(Deployment.Cause.RED_PIPELINE).build())
              .build());
      when(git.deployResolved(Path.of("/repo"), "ABC-1", "dev")).thenReturn(false);

      assertThatThrownBy(() -> deploys.deploy("a1"))
          .isInstanceOf(Refusal.class)
          .hasMessageContaining("waits on the session's fix in /ABC-1-deploy");

      assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
      assertThat(state.task("ABC-1").orElseThrow().message())
          .startsWith("red pipeline on the deploy commit");
      verify(git, never()).mergeIntoAndPush(any(), anyString(), anyString());
    }
  }

  @Nested
  class ApiAndWebApproved {

    @BeforeEach
    void bothRepositoriesLandOnDev() {
      state.putTask("ABC-1", apiAndWebTask().build());
      when(git.mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev")).thenReturn("cafebabe1234");
      when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");
    }

    @Test
    void landsEveryRepositoryTaskSpansInTheOrderItHoldsThem() {
      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.message())
          .contains("api into dev (cafebabe)", "web into dev (f00dfeed)", "DEPLOYED");
      assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().deployment()).targets())
          .extracting(target -> target.landing().merge())
          .containsExactly("cafebabe1234", "f00dfeed5678");
    }

    @Test
    void namesWhatIsLiveAndWhatIsNotWhenRepositoryConflictsAfterAnotherHasLanded() {
      Path deployWorktree = root.resolve("ABC-1-deploy");
      doThrow(
              new GitDeploy.MergeConflictException(
                  "ABC-1", "dev", "conflict in Widget.java", deployWorktree))
          .when(git)
          .mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");

      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
      assertThat(outcome.message())
          .contains(
              "CONFLICT merging web into dev",
              "Live on the deploy branch: api",
              "NOT deployed: web",
              deployWorktree.toString());
      assertThat(outcome.stamp()).contains("Live on the deploy branch: api", "NOT deployed: web");
      assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().deployment()).targets())
          .extracting(target -> target.landing().merge())
          .containsExactly("cafebabe1234", null);
    }

    @Test
    void startsFromTheTopWhenDeployWorktreeIsLeftOverFromSomeRoundOtherThanConflict() {
      when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);

      deploys.deploy("a1");

      verify(git).mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");
      verify(git).mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");
    }

    @Test
    void landsTheRepositoriesThatHaveWorkAndPassesOverTheOnesWithNothingToDeploy() {
      doThrow(new GitDeploy.NothingToDeployException("ABC-1", "dev"))
          .when(git)
          .mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");

      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.message())
          .contains("web into dev (f00dfeed)", "nothing to deploy in api", "DEPLOYED")
          .doesNotContain("api into dev");
    }

    @Test
    void refusesTheDeployWhenNoRepositoryHasAnythingToDeploy() {
      doThrow(new GitDeploy.NothingToDeployException("ABC-1", "dev"))
          .when(git)
          .mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));

      assertThatThrownBy(() -> deploys.deploy("a1"))
          .isInstanceOf(GitDeploy.NothingToDeployException.class)
          .hasMessageContaining("Nothing to deploy");
      assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    void reportsAndRecordsWhatIsLiveWhenDeployBreaksOffForReasonNoWorktreeCanFix() {
      doThrow(new IllegalStateException("Deploy push to dev was rejected"))
          .when(git)
          .mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");

      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
      assertThat(outcome.message())
          .contains(
              "Live on the deploy branch: api",
              "NOT deployed: web",
              "Deploy push to dev was rejected");
      assertThat(outcome.stamp()).contains("Live on the deploy branch: api", "NOT deployed: web");
      assertThat(outcome.cause()).hasMessage("Deploy push to dev was rejected");
    }

    @Test
    void leavesNoDanglingWordWhenTheFailureItReportsCarriesNoMessage() {
      doThrow(new NullPointerException())
          .when(git)
          .mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev");

      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
      assertThat(outcome.message()).endsWith("NOT deployed: web.");
    }

    @Test
    void refusesTheWholeDeployWhenAnyRepositoryHasNoDeployBranch() {
      projects = deployProjects().withWebDeployBranch(null).build();

      assertThatThrownBy(() -> deploys.deploy("a1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("web")
          .hasMessageContaining("deployBranch");
      verifyNoInteractions(git);
    }
  }

  @Nested
  class ApiAndWebInProgress {

    @Test
    void countsNothingAsLiveWhenTheFirstRepositoryOfFreshRoundConflicts() {
      state.putTask("ABC-1", apiAndWebTask().withStatus(TaskStatus.REVIEWED).build());
      state.updateTask(
          "ABC-1",
          t ->
              t.withDeployment(
                  deployment()
                      .withTargets(
                          List.of(
                              Deployment.Target.of("api", "dev").withMerge("0ldc0mm1t111"),
                              Deployment.Target.of("web", "dev").withMerge("0ldc0mm1t222")))
                      .build()));
      doThrow(
              new GitDeploy.MergeConflictException(
                  "ABC-1", "dev", "conflict", root.resolve("ABC-1-deploy")))
          .when(git)
          .mergeIntoAndPush(Path.of("/repo/api"), "ABC-1", "dev");

      Outcome outcome = deploys.deploy("a1");

      assertThat(outcome.message())
          .contains("Live on the deploy branch: none", "NOT deployed: api, web");
    }

    @Test
    void repeatedDeployPicksUpAtTheRepositoryTheConflictLeftBehind() {
      state.putTask(
          "ABC-1",
          apiAndWebTask()
              .withStatus(TaskStatus.DEPLOY_CONFLICT)
              .withDeployment(apiMergedAndWebConflicted().build())
              .build());
      when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
      when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");

      Outcome outcome = deploys.deploy("a1");

      verify(git, never()).mergeIntoAndPush(eq(Path.of("/repo/api")), anyString(), anyString());
      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.message())
          .contains("web into dev (f00dfeed)", "already on the deploy branch: api (cafebabe)");
    }

    @Test
    void pressAfterTheConflictedRepositoryLeftLandsTheUnpushedOnesWithoutOpeningAgain() {
      state.putTask("ABC-1", apiAndWebTask().withStatus(TaskStatus.DEPLOY_CONFLICT).build());
      state.updateTask(
          "ABC-1",
          t ->
              t.withDeployment(
                  deployment()
                      .withTargets(
                          List.of(
                              Deployment.Target.of("api", "dev").withMerge("cafebabe1234"),
                              Deployment.Target.of("web", "dev")))
                      .build()));
      when(git.mergeIntoAndPush(Path.of("/repo/web"), "ABC-1", "dev")).thenReturn("f00dfeed5678");

      Outcome outcome = deploys.deploy("a1");

      verify(git, never()).mergeIntoAndPush(eq(Path.of("/repo/api")), anyString(), anyString());
      assertThat(outcome.message())
          .contains("web into dev (f00dfeed)", "already on the deploy branch: api (cafebabe)");
      assertThat(
              Objects.requireNonNull(state.task("ABC-1").orElseThrow().deployment())
                  .targets()
                  .getFirst()
                  .landing()
                  .merge())
          .isEqualTo("cafebabe1234");
    }

    @Test
    void refusesPressWhenTheRecordedConflictsDeployWorktreeIsGoneInsteadOfMergingAgain() {
      TaskState opened =
          reducer.deployStepped(
              apiAndWebTask().withStatus(TaskStatus.DEPLOY_CONFLICT).build(),
              new DeployStep.Opened(
                  List.of(Deployment.Target.of("api", "dev"), Deployment.Target.of("web", "dev"))),
              1L);
      TaskState pushed = reducer.deployStepped(opened, new DeployStep.Pushed("api", 2L), 2L);
      state.putTask(
          "ABC-1",
          reducer.deployStepped(
              pushed, new DeployStep.Conflicted("web", Deployment.Cause.MERGE), 3L));

      assertThatThrownBy(() -> deploys.deploy("a1"))
          .isInstanceOf(Refusal.class)
          .hasMessageContaining("/repo/ABC-1-deploy");

      verify(git, never()).mergeIntoAndPush(any(), anyString(), anyString());
    }
  }
}
