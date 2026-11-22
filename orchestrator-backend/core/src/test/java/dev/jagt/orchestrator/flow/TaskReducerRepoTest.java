package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class TaskReducerRepoTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));
  private final TaskState openedThree =
      reducer.deployStepped(
          TaskState.builder(
                  List.of(
                      new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                      new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta"),
                      new TaskRepo(
                          "gamma", "/gamma-wt", "git@host:g/gamma.git", "http://mr/gamma")),
                  TaskStatus.DEPLOY_CONFLICT)
              .build(),
          new DeployStep.Opened(
              List.of(
                  Deployment.Target.of("alpha", "dev"),
                  Deployment.Target.of("beta", "dev"),
                  Deployment.Target.of("gamma", "dev"))),
          1_000);
  private final TaskState openedTwo =
      reducer.deployStepped(
          TaskState.builder(
                  List.of(
                      new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha"),
                      new TaskRepo("beta", "/beta-wt", "git@host:g/beta.git", "http://mr/beta")),
                  TaskStatus.DEPLOY_CONFLICT)
              .build(),
          new DeployStep.Opened(
              List.of(Deployment.Target.of("alpha", "dev"), Deployment.Target.of("beta", "dev"))),
          1_000);

  @Test
  void secondGitChangeFoundReplacesTheOneParked() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .gitTrust(GitTrust.of(Map.of("a", "1")))
            .build();
    TaskState first = reducer.gitChanged(task, "a changed");

    TaskState second = reducer.gitChanged(first, "a changed; b added");

    assertThat(Objects.requireNonNull(second.gate().gitTrust()).changed())
        .isEqualTo("a changed; b added");
  }

  @Test
  void repositoryAttachedEndsTheRequestForOne() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState asked = reducer.repoAsked(task, "registry");

    TaskState attached = reducer.repoAttached(asked, TaskRepo.of("registry", "/wt-registry"));

    assertThat(attached.work().asksForRepository()).isFalse();
    assertThat(attached.repos()).extracting(TaskRepo::project).contains("registry");
  }

  @Test
  void placesTaskInTheRepositoriesItsPlanNamedInPlaceOfItsScratchFolder() {
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of(null, "/scratch/ABC-9")), TaskStatus.PLAN_PENDING)
            .baseBranch("origin/feature/parent")
            .build();

    TaskState placed =
        reducer.placed(
            task,
            List.of(TaskRepo.of("api", "/wt-api"), TaskRepo.of("web", "/wt-web")),
            "feature/parent");

    assertThat(placed.projects()).containsExactly("api", "web");
    assertThat(placed.standing().status()).isEqualTo(TaskStatus.PLAN_PENDING);
    assertThat(placed.ticket().baseBranch()).isEqualTo("feature/parent");
  }

  @Test
  void placesTaskThatAskedForRepositoryWithoutKeepingTheRequest() {
    TaskState task =
        reducer.repoAsked(
            TaskState.builder(List.of(TaskRepo.of(null, "/scratch/ABC-9")), TaskStatus.PLAN_PENDING)
                .build(),
            "registry");

    TaskState placed = reducer.placed(task, List.of(TaskRepo.of("api", "/wt-api")), null);

    assertThat(placed.work().asksForRepository()).isFalse();
    assertThat(placed.ticket().baseBranch()).isNull();
  }

  @Test
  void reportNeedingNoRepositoryEndsTheRequestForOne() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).repoRequest("registry").build();

    TaskState reported = reducer.repoAsked(task, null);

    assertThat(reported.work().asksForRepository()).isFalse();
  }

  @Test
  void newHeadMakesTheChecksReadOnTheOldOneStale() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).build();
    TaskState read = reducer.checksRead(reducer.headSeen(task, "t1"), "failed", "t1");

    TaskState moved = reducer.headSeen(read, "t2");

    assertThat(read.checks().read()).isEqualTo("failed");
    assertThat(moved.checks().read()).isNull();
  }

  @Test
  void anApprovalReadIsKeptAsTheHostSaidIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.REVIEWED).build();

    TaskState approved = reducer.approvalRead(task, true);

    assertThat(approved.round().approved()).isTrue();
  }

  @Test
  void disarmsTheInfraRetryArmedForTheCommitBeforeWhenTheHeadMoves() {
    InfraRetry armed =
        InfraRetry.armed(
            9_000, "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .headTip("aaa")
            .infraRetries(List.of(armed))
            .build();

    TaskState moved = reducer.headSeen(read, "bbb");

    assertThat(read.checks().infraRetries()).containsExactly(armed);
    assertThat(moved.checks().infraRetries()).isEmpty();
  }

  @Test
  void keepsTheInfraRetryArmedWhenTheSameHeadIsSeenAgain() {
    InfraRetry armed =
        InfraRetry.armed(
            9_000, "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .headTip("aaa")
            .infraRetries(List.of(armed))
            .build();

    TaskState again = reducer.headSeen(read, "aaa");

    assertThat(again.checks().infraRetries()).containsExactly(armed);
  }

  @Test
  void forgetsWhatWasReadAboutRequestWhenTheTaskIsPointedAtAnotherOne() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .mrUrl("https://host/mr/1")
            .approved(true)
            .pipelineStatus("success")
            .requestOpenedAt(1_700_000_000_000L)
            .build();

    TaskState relinked =
        reducer.requestLinked(task, Map.of(), "https://host/mr/2", false, 1_700_000_005_000L);

    assertThat(relinked.round().approved()).isNull();
    assertThat(relinked.checks().pipelineStatus()).isNull();
    assertThat(relinked.round().requestOpenedAt()).isEqualTo(1_700_000_005_000L);
  }

  @Test
  void forgetsTheInfraRetriesOfRoundWhenNewRoundIsShipped() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/mr/9")
            .infraRetries(
                List.of(
                    InfraRetry.armed(
                        5_000,
                        "shop",
                        "https://host/p/1",
                        "unit-tests",
                        "runner_system_failure",
                        1,
                        3)))
            .build();

    TaskState round = reducer.requestLinked(polling, Map.of(), "https://host/mr/9", true, 2_000L);

    assertThat(round.checks().infraRetries()).isEmpty();
  }

  @Test
  void readsTheRequestsMergedOnceTheOnlyOpenOneLeavesTheTask() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api"),
                    new TaskRepo("web", "/web-wt", "git@host:g/web.git", "http://mr/web")),
                TaskStatus.CI_POLLING)
            .build()
            .withMergedByHost("api");

    TaskState left = reducer.repoLeft(task, "web");

    assertThat(left.projects()).containsExactly("api");
    assertThat(left.requestsMerged()).isTrue();
  }

  @Test
  void keepsTheOnlyRepositoryOfTaskWhoseRequestWasClosed() {
    TaskState task =
        TaskState.builder(
                List.of(new TaskRepo("api", "/wt", "git@host:g/api.git", "http://mr/api")),
                TaskStatus.CI_POLLING)
            .build();

    TaskState left = reducer.repoLeft(task, "api");

    assertThat(left.projects()).containsExactly("api");
  }

  @Test
  void keepsTheLandedTargetInTheRecordWhereItsRepositoryLeavesTheTask() {
    TaskState merged =
        reducer.deployStepped(
            reducer.deployStepped(
                reducer.deployStepped(openedThree, new DeployStep.Merged("alpha", "a1"), 1_100),
                new DeployStep.Merged("beta", "b1"),
                1_100),
            new DeployStep.Merged("gamma", "c1"),
            1_100);
    TaskState red =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "beta");

    assertThat(left.standing().status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(left.projects()).containsExactly("alpha", "gamma");
    assertThat(Objects.requireNonNull(left.work().deployment()).targets())
        .extracting(Deployment.Target::project)
        .containsExactly("alpha", "beta", "gamma");
    assertThat(left.work().deployment().tips()).contains("beta@b1");
    assertThat(left.work().deployment().targets()).noneMatch(Deployment.Target::conflicted);
  }

  @Test
  void leavesTheDeployLandingSoItsCheckIsAskedAgainWhereTheConflictedRepositoryLeavesTheTask() {
    TaskState merged =
        reducer.deployStepped(
            reducer.deployStepped(openedTwo, new DeployStep.Merged("alpha", "a1"), 1_100),
            new DeployStep.Merged("beta", "b1"),
            1_100);
    TaskState red =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "beta");
    TaskState asked = reducer.deployStepped(left, new DeployStep.Asked("dg"), 1_300);

    assertThat(DeployPhase.of(left.work().deployment())).isEqualTo(DeployPhase.LANDING);
    assertThat(DeployPhase.of(asked.work().deployment())).isEqualTo(DeployPhase.CHECK_ASKED);
  }

  @Test
  void keepsTheDeployConflictWhereTheConflictedRepositoryIsTheTasksOnlyOne() {
    TaskState task =
        TaskState.builder(
                List.of(
                    new TaskRepo("alpha", "/alpha-wt", "git@host:g/alpha.git", "http://mr/alpha")),
                TaskStatus.DEPLOY_CONFLICT)
            .build();
    TaskState opened =
        reducer.deployStepped(
            task, new DeployStep.Opened(List.of(Deployment.Target.of("alpha", "dev"))), 1_000);
    TaskState red =
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("alpha", Deployment.Cause.RED_PIPELINE), 1_100);

    TaskState left = reducer.repoLeft(red, "alpha");

    assertThat(left.standing().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(Objects.requireNonNull(left.work().deployment()).conflictedTarget()).isPresent();
  }

  @Test
  void offersDeployAgainWhereTheConflictedRepositoryLeavesAndNothingLanded() {
    TaskState conflicted =
        reducer.deployStepped(
            openedTwo, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_100);

    TaskState left = reducer.repoLeft(conflicted, "beta");

    assertThat(left.standing().status()).isEqualTo(TaskStatus.REVIEWED);
    assertThat(Objects.requireNonNull(left.work().deployment()).conflictedTarget()).isEmpty();
  }

  @Test
  void holdsTheDeployConflictOnTheUnpushedRepositoryWhereTheMergeConflictedOneLeavesTheTask() {
    TaskState merged =
        reducer.deployStepped(openedThree, new DeployStep.Merged("alpha", "a1"), 1_100);
    TaskState conflicted =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_200);

    TaskState left = reducer.repoLeft(conflicted, "beta");

    assertThat(left.standing().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(left.projects()).containsExactly("alpha", "gamma");
    assertThat(Objects.requireNonNull(left.work().deployment()).targets())
        .extracting(Deployment.Target::project)
        .containsExactly("alpha", "gamma");
    assertThat(left.work().deployment().at()).isEqualTo(1);
    assertThat(left.work().deployment().conflictedTarget()).isEmpty();
    assertThat(left.work().deployment().targets().getFirst().landing().merge()).isEqualTo("a1");
    assertThat(DeployPhase.of(left.work().deployment())).isEqualTo(DeployPhase.LANDING);
  }

  @Test
  void landsDeployedWhereTheMergeConflictedLastRepositoryLeavesAfterAnotherLanded() {
    TaskState merged =
        reducer.deployStepped(openedTwo, new DeployStep.Merged("alpha", "a1"), 1_100);
    TaskState conflicted =
        reducer.deployStepped(
            merged, new DeployStep.Conflicted("beta", Deployment.Cause.MERGE), 1_200);

    TaskState left = reducer.repoLeft(conflicted, "beta");

    assertThat(left.standing().status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(Objects.requireNonNull(left.work().deployment()).targets())
        .extracting(Deployment.Target::project)
        .containsExactly("alpha");
    assertThat(left.work().deployment().conflictedTarget()).isEmpty();
  }

  @Test
  void leavesNothingToPushWhereThePipelineConflictedRepositoryLeavesTheTask() {
    TaskState red =
        reducer.deployStepped(
            openedThree, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "beta");

    assertThat(Objects.requireNonNull(left.work().deployment()).at()).isEqualTo(2);
  }

  @Test
  void keepsTheDeployConflictWhereAnotherRepositoryThanTheConflictedOneLeavesTheTask() {
    TaskState red =
        reducer.deployStepped(
            openedThree, new DeployStep.Conflicted("beta", Deployment.Cause.RED_PIPELINE), 1_200);

    TaskState left = reducer.repoLeft(red, "gamma");

    assertThat(left.standing().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(
            Objects.requireNonNull(left.work().deployment())
                .conflictedTarget()
                .orElseThrow()
                .project())
        .isEqualTo("beta");
  }
}
