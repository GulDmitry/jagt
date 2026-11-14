package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestDeployPipelinesBuilder.deployPipelines;
import static dev.jagt.orchestrator.job.TestDeployedTaskBuilder.deployedTask;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.deploy.RedCause;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.port.codehost.CommitPipeline;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class DeployPipelinesTest {

  private static final String REMOTE = "https://git.example.invalid/shop/web.git";
  private static final String SHA = "0123456789abcdef";
  private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

  private final CodeHost pipelines = mock(CodeHost.class);
  private final RedCauses causes = mock(RedCauses.class);
  private final DeployFixWorktrees fixes = mock(DeployFixWorktrees.class);
  private final DeployPipelines deploys =
      deployPipelines()
          .build(
              pipelines, causes, fixes, stateService().build(), Clock.fixed(NOW, ZoneOffset.UTC));
  private final TaskState deployed = deployedTask().build();
  private final TaskState deployedInTwo =
      deployedTask()
          .withRepos(
              List.of(
                  new TaskRepo("shop", "/wt", REMOTE, null),
                  new TaskRepo("api", "/wt2", REMOTE, null)))
          .withTargets(
              List.of(
                  Deployment.Target.of("shop", "dev").withMerge(SHA),
                  Deployment.Target.of("api", "dev").withMerge(SHA)))
          .build();
  private final TaskState redConflict =
      deployedTask()
          .withStatus(TaskStatus.DEPLOY_CONFLICT)
          .withTargets(
              List.of(
                  Deployment.Target.of("shop", "dev")
                      .withMerge(SHA)
                      .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))))
          .withOpen(0)
          .build();

  static Stream<Arguments> readsAfterThePush() {
    return Stream.of(
        arguments(
            9,
            new DeployPipelines.Reading(
                DeployPipelines.Kind.WAITING,
                "shop 01234567: pipeline not started",
                "",
                "",
                List.of())),
        arguments(
            10,
            new DeployPipelines.Reading(DeployPipelines.Kind.GREEN, "", "", "", List.of("shop"))));
  }

  @Test
  void deployStandsOnceThePipelineOnItsCommitIsGreen() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new CommitPipeline(
                        "success", "https://git.example.invalid/shop/web/-/pipelines/7")),
                TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    verify(causes).deployStands("ABC-42");
  }

  @ParameterizedTest
  @CsvSource({"success,true", "failed,false", "running,false", "none,false"})
  void redPipelineConflictIsDisprovedOnlyByGreenPipelineOnTheDeployBranchSinceItsCommit(
      String status, boolean disproved) {
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline(status, "")), TokenUsage.NONE));

    assertThat(deploys.redConflictDisproved(redConflict)).isEqualTo(disproved);
  }

  @Test
  void redPipelineConflictInTwoRepositoriesStandsWhileOneOfThemIsStillRed() {
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("success", "")), TokenUsage.NONE));
    when(pipelines.onBranchSince("api", REMOTE, "dev", SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("failed", "")), TokenUsage.NONE));
    TaskState inTwo =
        deployedTask()
            .withRepos(
                List.of(
                    new TaskRepo("shop", "/wt", REMOTE, null),
                    new TaskRepo("api", "/wt2", REMOTE, null)))
            .withStatus(TaskStatus.DEPLOY_CONFLICT)
            .withTargets(
                List.of(
                    Deployment.Target.of("shop", "dev")
                        .withMerge(SHA)
                        .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1)),
                    Deployment.Target.of("api", "dev")
                        .withMerge(SHA)
                        .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))))
            .withOpen(0)
            .build();

    assertThat(deploys.redConflictDisproved(inTwo)).isFalse();
  }

  @Test
  void redPipelineConflictStandsWhereTheDeployBranchCannotBeRead() {
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(Answer.failed(TokenUsage.NONE, "HTTP 500"));

    assertThat(deploys.redConflictDisproved(redConflict)).isFalse();
  }

  @Test
  void redPipelineConflictOfRepositoryWithNoRemoteStands() {
    TaskState noRemote =
        deployedTask()
            .withRepos(List.of(new TaskRepo("shop", "/wt", null, null)))
            .withStatus(TaskStatus.DEPLOY_CONFLICT)
            .withTargets(
                List.of(
                    Deployment.Target.of("shop", "dev")
                        .withMerge(SHA)
                        .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))))
            .withOpen(0)
            .build();

    assertThat(deploys.redConflictDisproved(noRemote)).isFalse();

    verifyNoInteractions(pipelines);
  }

  @Test
  void redPipelineConflictOfRepositoryTheTaskNoLongerHoldsIsDisproved() {
    TaskState left =
        deployedTask()
            .withStatus(TaskStatus.DEPLOY_CONFLICT)
            .withTargets(
                List.of(
                    Deployment.Target.of("api", "dev")
                        .withMerge(SHA)
                        .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1)),
                    Deployment.Target.of("shop", "dev").withMerge(SHA)))
            .withOpen(0)
            .build();

    assertThat(deploys.redConflictDisproved(left)).isTrue();

    verifyNoInteractions(pipelines);
  }

  @Test
  void mergeConflictIsNeverReadAgainstThePipelines() {
    TaskState conflicted =
        deployedTask()
            .withStatus(TaskStatus.DEPLOY_CONFLICT)
            .withTargets(
                List.of(
                    Deployment.Target.of("shop", "dev")
                        .withMerge(SHA)
                        .withConflict(Conflict.of(Deployment.Cause.MERGE, 1))))
            .withOpen(0)
            .build();

    assertThat(deploys.redConflictDisproved(conflicted)).isFalse();

    verifyNoInteractions(pipelines);
  }

  @Test
  void deployStaysOpenWhileThePipelineOnItsCommitIsRunning() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new CommitPipeline(
                        "running", "https://git.example.invalid/shop/web/-/pipelines/7")),
                TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
    assertThat(reading.said()).isEqualTo("shop 01234567: pipeline running");
  }

  @Test
  void deployStaysOpenWhereTheHostCannotBeRead() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(Answer.failed(TokenUsage.NONE, "glab not logged in to git.example.invalid"));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.UNREAD);
    assertThat(reading.said())
        .isEqualTo("shop 01234567: pipeline unread, glab not logged in to git.example.invalid");
  }

  @Test
  void deployWaitsWithoutBeingUnreadWhereJagtCannotReachTheHost() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(
            Answer.unreached(
                new ReachLost(
                    "glab",
                    "git.example.invalid",
                    List.of("glab", "auth", "login", "--hostname", "git.example.invalid"),
                    "glab not logged in to git.example.invalid")));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.UNREACHED);
  }

  @Test
  void theFirstOfTwoRepositoriesStillRunningIsTheOneTheLineNames() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("running", "")), TokenUsage.NONE));
    when(pipelines.onCommit("api", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("running", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployedInTwo);

    assertThat(reading.said()).startsWith("shop ");
  }

  @Test
  void readThatReachedTheHostOutranksOneThatDidNotWhenTwoRepositoriesAreOpen() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(
            Answer.unreached(
                new ReachLost(
                    "glab",
                    "git.example.invalid",
                    List.of(),
                    "glab not logged in to git.example.invalid")));
    when(pipelines.onCommit("api", REMOTE, SHA))
        .thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1: 500"));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployedInTwo);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.UNREAD);
  }

  @Test
  void deployWhosePipelineDiedOnInfrastructureWaitsForItsRetry() {
    String url = "https://git.example.invalid/shop/web/-/pipelines/7";
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("failed", url)), TokenUsage.NONE));
    when(causes.judgeDeploy("ABC-42", "shop", url))
        .thenReturn(
            new RedCauses.Verdict(
                RedCause.INFRASTRUCTURE,
                true,
                "runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC",
                Optional.empty()));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
    assertThat(reading.said())
        .isEqualTo(
            "shop 01234567: pipeline red on infrastructure, "
                + "runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC");
  }

  @Test
  void deployIsReadOnTheFixPushedAfterItsMergeNotOnTheMerge() {
    TaskState fixed =
        deployedTask()
            .withTargets(
                List.of(
                    Deployment.Target.of("shop", "dev").withMerge(SHA).withFix("f1x0123456789")))
            .build();
    when(pipelines.onCommit("shop", REMOTE, "f1x0123456789"))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("success", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", fixed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
  }

  @ParameterizedTest
  @MethodSource("readsAfterThePush")
  void hostListingNoPipelineIsWaitedOnOnlyWithinTheGraceAfterThePush(
      long minutes, DeployPipelines.Reading expected) {
    TaskState pushed =
        deployedTask()
            .withTargets(
                List.of(
                    Deployment.Target.of("shop", "dev")
                        .withMerge(SHA)
                        .withPushedAt(NOW.minusSeconds(minutes * 60).toEpochMilli())))
            .build();
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

    assertThat(reading).isEqualTo(expected);
  }

  @Test
  void deployStandsWhenTheHostListsNoPipelineTenMinutesAfterThePushWhateverTheStatusSince() {
    TaskState pushed =
        TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOYED)
            .lastActiveTimestamp(NOW.minusSeconds(60).toEpochMilli())
            .deployment(
                Deployment.defaults()
                    .withTargets(
                        List.of(
                            Deployment.Target.of("shop", "dev")
                                .withMerge(SHA)
                                .withPushedAt(NOW.minusSeconds(11 * 60).toEpochMilli()))))
            .build();
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
  }

  @Test
  void deployStandsWhereTheRepositoryHasNoCodeHostAddress() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
  }

  @Test
  void deployStandsWhereNoRemoteWasRecordedForTheRepository() {
    TaskState unrecorded =
        deployedTask().withRepos(List.of(new TaskRepo("shop", "/wt", null, null))).build();

    DeployPipelines.Reading reading = deploys.read("ABC-42", unrecorded);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
  }

  @Test
  void deployStaysOpenWhileTheHostDoesNotAnswerAndStandsOnceItDoes() {
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(
            Answer.failed(TokenUsage.NONE, "glab exited 1"),
            new Answer<>(Optional.of(new CommitPipeline("success", "")), TokenUsage.NONE));

    DeployPipelines.Reading unanswered = deploys.read("ABC-42", deployed);
    DeployPipelines.Reading answered = deploys.read("ABC-42", deployed);

    assertThat(unanswered.kind()).isEqualTo(DeployPipelines.Kind.UNREAD);
    assertThat(answered.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
  }
}
