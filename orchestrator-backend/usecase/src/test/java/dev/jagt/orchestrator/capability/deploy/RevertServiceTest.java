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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.service.git.GitRevert;
import dev.jagt.orchestrator.service.git.RevertConflictException;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

class RevertServiceTest {

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final GitDeploy git = mock(GitDeploy.class);
  private final GitRevert revert = mock(GitRevert.class);
  private ConfigFile projects = deployProjects().build();
  private final GitCaseFacts facts = mock(GitCaseFacts.class);
  private final GitCaseFacts.Candidate listed =
      new GitCaseFacts.Candidate("aaaaaaaa1111", "2026-11-01", "Merge ABC-1");
  private StateService state;
  private RevertService service;

  @BeforeEach
  void everyProjectDeploysOntoDev() {
    state = stateService().withRoot(root).build();
    when(config.load()).thenAnswer(call -> projects);
    when(config.project(anyString()))
        .thenAnswer(call -> projects.projects().get(call.getArgument(0, String.class)));
    service =
        new RevertService(
            state,
            new DeployTargets(config),
            new RevertRunner(state, git, revert, new FakeClock()),
            revert,
            facts);
    when(facts.picked(List.of(listed), "aaaaaaa")).thenReturn(Optional.of(listed));
    when(revert.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("aaaaaaaa1111"), anyList()))
        .thenReturn("rev1234567");
  }

  @Test
  void refusesTaskThatIsNotThereNamingIt() {
    assertThatThrownBy(() -> service.revert("ABC-9"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("No task ABC-9")
        .extracting("code")
        .isEqualTo(Refusal.Code.NO_SUCH_TASK);
  }

  @Test
  void revertsTheFixesOfDeployWithItsMergeNewestFirst() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .alias("a1")
            .deployment(
                deployment()
                    .withTargets(
                        List.of(
                            Deployment.Target.of("proj", "dev")
                                .withMerge("cafebabe1234")
                                .withFix("f1x1")
                                .withFix("f1x2")))
                    .build())
            .build());
    when(revert.revertMergeAndPush(
            any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), eq(List.of("f1x2", "f1x1"))))
        .thenReturn("f00dfeed5678");

    Outcome outcome = service.revert("a1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().deployment()).live())
        .isFalse();
    verify(revert)
        .revertMergeAndPush(
            any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), eq(List.of("f1x2", "f1x1")));
  }

  @Test
  void revertsPreUpgradeDeployOnTheProjectsConfiguredDeployBranch() {
    state.putTask(
        "ABC-1",
        new JsonMapper()
            .readValue(
                """
                {"repos":[{"project":"proj","worktreePath":"/wt","deployCommit":"cafebabe1234"}],
                 "status":"DEPLOYED","alias":"a1","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class));
    when(revert.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList()))
        .thenReturn("f00dfeed5678");

    service.revert("a1");

    verify(revert).revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList());
  }

  @Test
  void listsEveryRepositoryEvenOneWhoseProjectLeftTheConfiguration() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("gone", "/gone-wt")),
                TaskStatus.DEPLOYED)
            .alias("a1")
            .build());

    assertThatThrownBy(() -> service.revert("a1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("api: on dev no merge names the task")
        .hasMessageContaining("gone: no deploy branch is configured");
  }

  @Test
  void revertsWhatLandedWhenRepositoryThatNeverLandedHasLeftTheConfiguration() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("gone", "/gone-wt")),
                TaskStatus.DEPLOY_CONFLICT)
            .alias("a1")
            .build());
    state.updateTask(
        "ABC-1",
        t ->
            t.withDeployment(
                deployment()
                    .withTargets(
                        List.of(
                            Deployment.Target.of("api", "dev").withMerge("cafebabe1234"),
                            Deployment.Target.of("gone", "dev")))
                    .build()));
    when(config.project("gone")).thenThrow(new IllegalArgumentException("Unknown project 'gone'"));
    when(revert.revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of()))
        .thenReturn("beef00991122");

    Outcome outcome = service.revert("a1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
  }

  @Nested
  class OneProjectLanded {

    @BeforeEach
    void theMergeWasRecorded() {
      state.putTask(
          "ABC-1",
          TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
              .alias("a1")
              .deployment(
                  deployment()
                      .withTargets(
                          List.of(Deployment.Target.of("proj", "dev").withMerge("cafebabe1234")))
                      .build())
              .build());
    }

    @Test
    void revertsTheDeployedMergeAndReportsTheUndoAsDone() {
      when(revert.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList()))
          .thenReturn("f00dfeed5678");

      Outcome outcome = service.revert("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.message()).contains("f00dfeed");
      assertThat(outcome.stamp()).isEqualTo("reverted on dev (f00dfeed)");
    }

    @Test
    void revertsOnTheBranchTheMergeLandedOnWhenTheConfigurationMovedSince() {
      projects = deployProjects().withProjDeployBranch("staging").build();
      when(revert.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList()))
          .thenReturn("f00dfeed5678");

      service.revert("a1");

      verify(revert)
          .revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList());
      verify(revert, never())
          .revertMergeAndPush(any(), anyString(), eq("staging"), anyString(), anyList());
    }

    @Test
    void refusesToUndoDeployOntoBranchThatIsTheBaseBranch() {
      projects = deployProjects().withProjDeployBranch("main").build();

      assertThatThrownBy(() -> service.revert("a1"))
          .hasMessageContaining("deployBranch equals the base branch");
      verify(revert, never())
          .revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void handsConflictingRevertBackAsConflictNamingItsWorktreeAndPushesNothing() {
      when(revert.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList()))
          .thenThrow(
              new RevertConflictException(
                  "cafebabe1234", "dev", "details", Path.of("/ABC-1-revert")));

      Outcome outcome = service.revert("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
      assertThat(outcome.message()).contains("/ABC-1-revert", "dev");
      Deployment.Target stopped =
          Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().deployment())
              .conflictedTarget()
              .orElseThrow();
      assertThat(Objects.requireNonNull(stopped.progress().conflict()).cause())
          .isEqualTo(Deployment.Cause.REVERT);
      assertThat(stopped.landed()).isTrue();
    }
  }

  @Nested
  class OneProjectWithoutRecordedMerge {

    @BeforeEach
    void theDeployRecordedNoMerge() {
      state.putTask(
          "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
    }

    @Test
    void refusesToGuessTheMergeCommitOfDeployItDidNotRecord() {
      assertThatThrownBy(() -> service.revert("a1"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("`revert ABC-1 <sha>` takes the one you pick out")
          .hasMessageContaining("proj: on dev no merge names the task")
          .hasMessageNotContaining("by hand");
      verify(revert, never())
          .revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList());
      assertThat(state.task("ABC-1").orElseThrow().standing().status())
          .isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void listsTheCandidateMergesNewestFirstWhenNoMergeWasRecorded() {
      when(facts.mergeCandidates(Path.of("/repo"), "dev", "ABC-1"))
          .thenReturn(
              List.of(
                  new GitCaseFacts.Candidate("bbbbbbbb2222", "2026-11-02", "Merge ABC-1 again"),
                  new GitCaseFacts.Candidate("aaaaaaaa1111", "2026-11-01", "Merge ABC-1")));

      assertThatThrownBy(() -> service.revert("a1"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "bbbbbbbb 2026-11-02 Merge ABC-1 again; aaaaaaaa 2026-11-01 Merge ABC-1");
    }

    @Test
    void revertsThePickedMergeWithItsFullShaWhenNoMergeWasRecorded() {
      when(facts.mergeCandidates(Path.of("/repo"), "dev", "ABC-1")).thenReturn(List.of(listed));

      Outcome outcome = service.revertPicked("a1", "aaaaaaa");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
    }

    @Test
    void refusesPickThatIsNoneOfTheCandidates() {
      assertThatThrownBy(() -> service.revertPicked("a1", "deadbeef"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("`deadbeef` is none of the candidates");
      verify(revert, never())
          .revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList());
    }
  }

  @Nested
  class ApiAndWebWithoutRecordedMerge {

    @BeforeEach
    void webDeploysOntoStaging() {
      state.putTask("ABC-1", apiAndWebTask().withStatus(TaskStatus.DEPLOYED).build());
      projects = deployProjects().withWebDeployBranch("staging").build();
    }

    @Test
    void leavesTheOtherRepositoryLiveWhenThePickedMergeIsOneOfSeveral() {
      when(facts.mergeCandidates(Path.of("/repo/api"), "dev", "ABC-1")).thenReturn(List.of(listed));

      Outcome outcome = service.revertPicked("a1", "aaaaaaa");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
      assertThat(outcome.message()).contains("api").contains("still live");
    }

    @Test
    void listsCandidatesOfEveryRepositoryWhenNoMergeCommitWasEverRecorded() {
      assertThatThrownBy(() -> service.revert("a1"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("api: on dev no merge names the task")
          .hasMessageContaining("web: on staging no merge names the task");
    }
  }

  @Nested
  class ApiAndWebLanded {

    @BeforeEach
    void bothMergesWereRecorded() {
      state.putTask("ABC-1", apiAndWebTask().withStatus(TaskStatus.DEPLOYED).build());
      state.updateTask(
          "ABC-1",
          t ->
              t.withDeployment(
                  deployment()
                      .withTargets(
                          List.of(
                              Deployment.Target.of("api", "dev").withMerge("cafebabe1234"),
                              Deployment.Target.of("web", "dev").withMerge("f00dfeed5678")))
                      .build()));
    }

    @Test
    void undoesTheRepositoriesInReverseOrderAndForgetsEachMergeItTookOut() {
      when(revert.revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList()))
          .thenReturn("beef00991122");

      Outcome outcome = service.revert("a1");

      InOrder undone = inOrder(git, revert);
      undone
          .verify(revert)
          .revertMergeAndPush(Path.of("/repo/web"), "ABC-1", "dev", "f00dfeed5678", List.of());
      undone
          .verify(revert)
          .revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of());
      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.message()).contains("reverted web on dev", "api on dev", "REVERTED");
      assertThat(
              Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().deployment())
                  .targets())
          .extracting(target -> target.landing().merge())
          .containsOnlyNulls();
    }

    @Test
    void refusesTheUndoAsHalfDoneWhenOneRepositoryCouldNotBeReverted() {
      when(revert.revertMergeAndPush(
              Path.of("/repo/web"), "ABC-1", "dev", "f00dfeed5678", List.of()))
          .thenReturn("beef00991122");
      doThrow(
              new IllegalStateException(
                  "the revert conflicts with work done there since the deploy"))
          .when(revert)
          .revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of());

      Outcome outcome = service.revert("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
      assertThat(outcome.message()).contains("reverted web on dev", "api still live on dev");
      assertThat(outcome.stamp()).contains("reverted web on dev", "api still live on dev");
      assertThat(
              Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().deployment())
                  .targets())
          .extracting(target -> target.landing().merge())
          .containsExactly("cafebabe1234", null);
    }

    @Test
    void doesNotRevertAgainTheRepositoryThatWasRevertedBeforeAnotherConflicted() {
      state.updateTask(
          "ABC-1", t -> t.withDeployment(Objects.requireNonNull(t.work().deployment()).withAt(2)));
      when(revert.revertMergeAndPush(
              eq(Path.of("/repo/web")), eq("ABC-1"), eq("dev"), eq("f00dfeed5678"), anyList()))
          .thenReturn("beef00991122");
      when(revert.revertMergeAndPush(
              eq(Path.of("/repo/api")), eq("ABC-1"), eq("dev"), eq("cafebabe1234"), anyList()))
          .thenThrow(
              new RevertConflictException(
                  "cafebabe1234", "dev", "details", Path.of("/ABC-1-revert")))
          .thenReturn("0ddba11cafe0");
      when(revert.revertUnmerged(Path.of("/repo/api"), "ABC-1")).thenReturn("");
      Outcome first = service.revert("a1");
      state.updateTask("ABC-1", task -> task.withStatus(TaskStatus.DEPLOY_CONFLICT, "conflict", 2));

      Outcome outcome = service.revert("a1");

      assertThat(first.kind()).isEqualTo(Outcome.Kind.CONFLICT);
      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      verify(revert, times(1))
          .revertMergeAndPush(
              eq(Path.of("/repo/web")), anyString(), anyString(), anyString(), anyList());
      assertThat(
              Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().deployment()).live())
          .isFalse();
    }
  }

  @Nested
  class ApiLandedAndWebNeverDid {

    @BeforeEach
    void onlyTheApiMergeWasRecorded() {
      state.putTask("ABC-1", apiAndWebTask().withStatus(TaskStatus.DEPLOYED).build());
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
    }

    @Test
    void undoesTheRepositoryThatIsLiveWithoutEvenLookingUpOneThatNeverLanded() {
      when(revert.revertMergeAndPush(
              Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of()))
          .thenReturn("beef00991122");

      Outcome outcome = service.revert("a1");

      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.message()).contains("reverted api on dev (beef0099)", "REVERTED");
    }
  }

  @Nested
  class ApiLandedAndWebConflicted {

    @BeforeEach
    void webMergeLeftHalfMerge() {
      state.putTask(
          "ABC-1",
          apiAndWebTask()
              .withStatus(TaskStatus.DEPLOY_CONFLICT)
              .withDeployment(apiMergedAndWebConflicted().build())
              .build());
    }

    @Test
    void discardsTheHalfMergeTheConflictLeftWhenTheDeployIsRevertedFromIt() {
      when(git.hasDeployWorktree(Path.of("/repo/web"), "ABC-1")).thenReturn(true);
      when(revert.revertMergeAndPush(
              Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of()))
          .thenReturn("beef00991122");

      service.revert("a1");

      verify(git).discardDeploy(Path.of("/repo/web"), "ABC-1");
    }

    @Test
    void keepsTheConflictWaitingWhenTheRevertOfWhatLandedIsRefused() {
      doThrow(new IllegalStateException("the revert conflicts"))
          .when(revert)
          .revertMergeAndPush(Path.of("/repo/api"), "ABC-1", "dev", "cafebabe1234", List.of());

      assertThatThrownBy(() -> service.revert("a1")).hasMessage("the revert conflicts");
      verify(git, never()).discardDeploy(any(), anyString());
    }
  }

  @Nested
  class OneProjectConflicted {

    @Test
    void discardsTheConflictedMergeAndLandsTheUndoWhenNothingHadLanded() {
      state.putTask(
          "ABC-1",
          TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
              .alias("a1")
              .deployment(
                  deployment()
                      .withAt(0)
                      .withTargets(
                          List.of(
                              Deployment.Target.of("proj", "dev")
                                  .withConflict(Conflict.of(Deployment.Cause.MERGE, 1))))
                      .build())
              .build());
      when(git.hasDeployWorktree(Path.of("/repo"), "ABC-1")).thenReturn(true);

      Outcome outcome = service.revert("a1");

      verify(git).discardDeploy(Path.of("/repo"), "ABC-1");
      assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
      assertThat(outcome.stamp()).contains("discarded the conflicted merge into dev");
    }

    @Test
    void discardsTheConflictedMergeOfSecondDeployAfterRevertingTheFirst() {
      state.putTask(
          "ABC-1",
          projectTask()
              .withStatus(TaskStatus.DEPLOY_CONFLICT)
              .withDeployment(projectMergedAndConflictedBy(Deployment.Cause.MERGE).build())
              .build());
      when(git.hasDeployWorktree(Path.of("/repo"), "ABC-1")).thenReturn(true);
      when(revert.revertMergeAndPush(Path.of("/repo"), "ABC-1", "dev", "cafebabe1234", List.of()))
          .thenReturn("beef00991122");

      service.revert("a1");

      verify(git).discardDeploy(Path.of("/repo"), "ABC-1");
    }

    @Test
    void refusesToRevertWhilePathsAreStillUnmergedNamingThemAndPushesNothing() {
      state.putTask(
          "ABC-1",
          projectTask()
              .withStatus(TaskStatus.DEPLOY_CONFLICT)
              .withDeployment(projectMergedAndConflictedBy(Deployment.Cause.REVERT).build())
              .build());
      when(revert.revertUnmerged(Path.of("/repo"), "ABC-1")).thenReturn("src/A.java\nsrc/B.java");

      assertThatThrownBy(() -> service.revert("a1"))
          .isInstanceOf(Refusal.class)
          .hasMessageContaining("src/A.java src/B.java");
      verify(revert, never())
          .revertMergeAndPush(any(), anyString(), anyString(), anyString(), anyList());
      assertThat(
              Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().deployment())
                  .conflictedTarget())
          .isPresent();
    }
  }
}
