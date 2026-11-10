package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class DeployPipelinesTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private static final String REMOTE = "https://git.example.invalid/shop/web.git";
  private static final String SHA = "0123456789abcdef";
  private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

  private final CodeHost pipelines = mock(CodeHost.class);
  private final RedCauses causes = mock(RedCauses.class);
  private final DeployFixWorktrees fixes = mock(DeployFixWorktrees.class);
  private final DeployPipelines deploys =
      new DeployPipelines(
          pipelines,
          causes,
          fixes,
          Clock.fixed(NOW, ZoneOffset.UTC),
          new FlowReports(
              new StateService(
                  new JsonMapper(),
                  new OrchestratorPaths(OrchestratorProperties.defaults()),
                  reducer, event -> {}),
              Clock.fixed(NOW, ZoneOffset.UTC),
              new Checkers(new TaskChart(Composition.builtIn())),
              new FlowRules(new TaskChart(Composition.builtIn()))));
  private final TaskState deployed =
      TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOYED)
          .deployment(
              new Deployment(
                  1, List.of(Deployment.Target.of("shop", "dev").withMerge(SHA)), 1, null))
          .build();

  private static TaskState conflicted(Deployment.Cause cause) {
    return TaskState.builder(
            List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOY_CONFLICT)
        .deployment(
            new Deployment(
                1,
                List.of(
                    Deployment.Target.of("shop", "dev")
                        .withMerge(SHA)
                        .withConflict(Conflict.of(cause, 1))),
                0,
                null))
        .build();
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

  @Test
  void redPipelineConflictIsDisprovedByGreenPipelineOnTheDeployBranchSinceItsCommit() {
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new CommitPipeline(
                        "success", "https://git.example.invalid/shop/web/-/pipelines/9")),
                TokenUsage.NONE));

    assertThat(deploys.redConflictDisproved(conflicted(Deployment.Cause.RED_PIPELINE))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"failed", "running", "none"})
  void redPipelineConflictStandsWhileThePipelineOnTheDeployBranchSinceItsCommitIsNotGreen(
      String status) {
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline(status, "")), TokenUsage.NONE));

    assertThat(deploys.redConflictDisproved(conflicted(Deployment.Cause.RED_PIPELINE))).isFalse();
  }

  @Test
  void redPipelineConflictInTwoRepositoriesStandsWhileOneOfThemIsStillRed() {
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("success", "")), TokenUsage.NONE));
    when(pipelines.onBranchSince("api", REMOTE, "dev", SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("failed", "")), TokenUsage.NONE));
    TaskState inTwo =
        TaskState.builder(
                List.of(
                    new TaskRepo("shop", "/wt", REMOTE, null),
                    new TaskRepo("api", "/wt2", REMOTE, null)),
                TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("shop", "dev")
                            .withMerge(SHA)
                            .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1)),
                        Deployment.Target.of("api", "dev")
                            .withMerge(SHA)
                            .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))),
                    0,
                    null))
            .build();

    assertThat(deploys.redConflictDisproved(inTwo)).isFalse();
  }

  @Test
  void redPipelineConflictStandsWhereTheDeployBranchCannotBeRead() {
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(Answer.failed(TokenUsage.NONE, "HTTP 500"));

    assertThat(deploys.redConflictDisproved(conflicted(Deployment.Cause.RED_PIPELINE))).isFalse();
  }

  @Test
  void redPipelineConflictOfRepositoryWithNoRemoteStands() {
    TaskState noRemote =
        TaskState.builder(
                List.of(new TaskRepo("shop", "/wt", null, null)), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("shop", "dev")
                            .withMerge(SHA)
                            .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))),
                    0,
                    null))
            .build();

    assertThat(deploys.redConflictDisproved(noRemote)).isFalse();

    verifyNoInteractions(pipelines);
  }

  @Test
  void redPipelineConflictOfRepositoryTheTaskNoLongerHoldsIsDisproved() {
    TaskState left =
        TaskState.builder(
                List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("api", "dev")
                            .withMerge(SHA)
                            .withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1)),
                        Deployment.Target.of("shop", "dev").withMerge(SHA)),
                    0,
                    null))
            .build();

    assertThat(deploys.redConflictDisproved(left)).isTrue();

    verifyNoInteractions(pipelines);
  }

  @Test
  void mergeConflictIsNeverReadAgainstThePipelines() {
    assertThat(deploys.redConflictDisproved(conflicted(Deployment.Cause.MERGE))).isFalse();

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
    TaskState inTwo =
        TaskState.builder(
                List.of(
                    new TaskRepo("shop", "/wt", REMOTE, null),
                    new TaskRepo("api", "/wt2", REMOTE, null)),
                TaskStatus.DEPLOYED)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("shop", "dev").withMerge(SHA),
                        Deployment.Target.of("api", "dev").withMerge(SHA)),
                    1,
                    null))
            .build();

    DeployPipelines.Reading reading = deploys.read("ABC-42", inTwo);

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

    TaskState inTwo =
        TaskState.builder(
                List.of(
                    new TaskRepo("shop", "/wt", REMOTE, null),
                    new TaskRepo("api", "/wt2", REMOTE, null)),
                TaskStatus.DEPLOYED)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("shop", "dev").withMerge(SHA),
                        Deployment.Target.of("api", "dev").withMerge(SHA)),
                    1,
                    null))
            .build();

    DeployPipelines.Reading reading = deploys.read("ABC-42", inTwo);

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
        deployed.withDeployment(
            new Deployment(
                1,
                List.of(
                    Deployment.Target.of("shop", "dev").withMerge(SHA).withFix("f1x0123456789")),
                1,
                null));
    when(pipelines.onCommit("shop", REMOTE, "f1x0123456789"))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("success", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", fixed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
  }

  @Test
  void deployStaysOpenWhileTheHostListsNoPipelineWithinTheGraceAfterThePush() {
    TaskState pushed =
        TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOYED)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("shop", "dev")
                            .withMerge(SHA)
                            .withPushedAt(NOW.minusSeconds(9 * 60).toEpochMilli())),
                    1,
                    null))
            .build();
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
    assertThat(reading.said()).isEqualTo("shop 01234567: pipeline not started");
  }

  @Test
  void deployStandsOnceTheHostStillListsNoPipelineAfterTheGrace() {
    TaskState pushed =
        TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOYED)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("shop", "dev")
                            .withMerge(SHA)
                            .withPushedAt(NOW.minusSeconds(10 * 60).toEpochMilli())),
                    1,
                    null))
            .build();
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
  }

  @Test
  void namesTheRepositoryWhoseDeployStoodWithNoPipelineListedSoLaterOneIsStillRead() {
    TaskState pushed =
        TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOYED)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("shop", "dev")
                            .withMerge(SHA)
                            .withPushedAt(NOW.minusSeconds(10 * 60).toEpochMilli())),
                    1,
                    null))
            .build();
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

    assertThat(reading.bare()).containsExactly("shop");
  }

  @Test
  void deployStandsWhenTheHostListsNoPipelineTenMinutesAfterThePushWhateverTheStatusSince() {
    Deployment recorded =
        Deployment.defaults()
            .withTargets(
                List.of(
                    Deployment.Target.of("shop", "dev")
                        .withMerge(SHA)
                        .withPushedAt(NOW.minusSeconds(11 * 60).toEpochMilli())));
    TaskState pushed =
        TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null)), TaskStatus.DEPLOYED)
            .lastActiveTimestamp(NOW.minusSeconds(60).toEpochMilli())
            .deployment(recorded)
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
        TaskState.builder(List.of(new TaskRepo("shop", "/wt", null, null)), TaskStatus.DEPLOYED)
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge(SHA)), 1, null))
            .build();

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
