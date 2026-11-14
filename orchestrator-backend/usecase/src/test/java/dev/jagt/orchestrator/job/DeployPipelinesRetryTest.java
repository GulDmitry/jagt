package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestDeployPipelinesBuilder.deployPipelines;
import static dev.jagt.orchestrator.job.TestDeployedTaskBuilder.deployedTask;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.deploy.RedCause;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.port.codehost.CommitPipeline;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeployPipelinesRetryTest {

  private static final String REMOTE = "https://git.example.invalid/shop/web.git";
  private static final String SHA = "0123456789abcdef";
  private static final String URL = "https://git.example.invalid/shop/web/-/pipelines/7";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);

  @TempDir Path root;

  private final CodeHost pipelines = mock(CodeHost.class);
  private final RedCauses causes = mock(RedCauses.class);
  private final DeployFixWorktrees fixes = mock(DeployFixWorktrees.class);
  private StateService state;
  private DeployPipelines deploys;

  @BeforeEach
  void redOnTheCode() {
    state = stateService().withRoot(root).build();
    deploys = deployPipelines().build(pipelines, causes, fixes, state, CLOCK);
    when(pipelines.onCommit("shop", REMOTE, SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("failed", URL)), TokenUsage.NONE));
    when(causes.judgeDeploy("ABC-42", "shop", URL))
        .thenReturn(new RedCauses.Verdict(RedCause.CODE, false, "", Optional.empty()));
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")), TokenUsage.NONE));
  }

  static Stream<Arguments> redCommitsStandingOnLaterGreenPipeline() {
    return Stream.of(
        arguments(
            "after its re-run",
            Deployment.Target.of("shop", "dev").withMerge(SHA).withRetried(SHA)),
        arguments(
            "without re-running the red one", Deployment.Target.of("shop", "dev").withMerge(SHA)));
  }

  @Test
  void redPipelineIsReRunOnceBeforeItIsHandedBack() {
    state.putTask(
        "ABC-42",
        deployedTask()
            .withTargets(List.of(Deployment.Target.of("shop", "dev").withMerge(SHA)))
            .build());
    when(pipelines.retryFailed("shop", URL))
        .thenReturn(new Answer<>(Optional.of("re-run"), TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
    verify(fixes, never()).open(any(), anyString(), anyString());
    assertThat(
            Objects.requireNonNull(state.task("ABC-42").orElseThrow().deployment())
                .targets()
                .getFirst()
                .retriedTip())
        .isTrue();
  }

  @Test
  void redPipelineThatFailedAgainAfterItsOneReRunIsHandedBackWithThePipelineToFix() {
    state.putTask(
        "ABC-42",
        deployedTask()
            .withTargets(
                List.of(Deployment.Target.of("shop", "dev").withMerge(SHA).withRetried(SHA)))
            .build());

    DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

    assertThat(reading)
        .isEqualTo(
            new DeployPipelines.Reading(
                DeployPipelines.Kind.RED,
                "red pipeline on the deploy commit: shop 01234567",
                "shop",
                URL,
                List.of()));
    verify(pipelines, never()).retryFailed(anyString(), anyString());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("redCommitsStandingOnLaterGreenPipeline")
  void redDeployCommitStandsOnLaterGreenPipelineWithoutAnotherReRunOrFixWorktree(
      String situation, Deployment.Target target) {
    state.putTask("ABC-42", deployedTask().withTargets(List.of(target)).build());
    when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA))
        .thenReturn(
            new Answer<>(
                Optional.of(
                    new CommitPipeline(
                        "success", "https://git.example.invalid/shop/web/-/pipelines/9")),
                TokenUsage.NONE));

    DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    verify(fixes, never()).open(any(), anyString(), anyString());
    verify(pipelines, never()).retryFailed(anyString(), anyString());
  }

  @Test
  void redPipelineIsHandedBackAsItStandsWhereTheHostRefusesTheReRun() {
    state.putTask(
        "ABC-42",
        deployedTask()
            .withTargets(List.of(Deployment.Target.of("shop", "dev").withMerge(SHA)))
            .build());
    when(pipelines.retryFailed("shop", URL))
        .thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1: 403"));

    DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.RED);
  }

  @Test
  void redPipelineStaysOpenWhenTheWorktreeOfItsFixCannotBeOpened() {
    state.putTask(
        "ABC-42",
        deployedTask()
            .withTargets(
                List.of(Deployment.Target.of("shop", "dev").withMerge(SHA).withRetried(SHA)))
            .build());
    TaskState task = state.task("ABC-42").orElseThrow();
    doThrow(new IllegalStateException("the path holds another checkout"))
        .when(fixes)
        .open(task, "ABC-42", "shop");

    DeployPipelines.Reading reading = deploys.read("ABC-42", task);

    assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
    assertThat(reading.said())
        .isEqualTo(
            "shop 01234567: pipeline red on the code, its deploy worktree not opened: the path"
                + " holds another checkout");
  }
}
