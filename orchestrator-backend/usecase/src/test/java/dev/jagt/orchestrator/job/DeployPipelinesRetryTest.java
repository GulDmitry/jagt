package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployPipelinesRetryTest {

    private static final String REMOTE = "https://git.example.invalid/shop/web.git";
    private static final String SHA = "0123456789abcdef";
    private static final String URL = "https://git.example.invalid/shop/web/-/pipelines/7";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);

    private final PipelineRetry pipelines = mock(PipelineRetry.class);
    private final RedCauses causes = mock(RedCauses.class);
    private final DeployFixWorktrees fixes = mock(DeployFixWorktrees.class);

    private StateService stateWith(Path root, Deployment.Target target) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null)),
                TaskStatus.DEPLOYED).deployment(new Deployment(1, List.of(target), 1, null)).build());
        return state;
    }

    private void redOnTheCode() {
        when(pipelines.onCommit("shop", REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(
                new CommitPipeline("failed", URL)), TokenUsage.NONE));
        when(causes.judgeDeploy("ABC-42", "shop", URL))
                .thenReturn(new RedCauses.Verdict(RedCause.CODE, false, "", Optional.empty()));
        when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA)).thenReturn(new Answer<>(Optional.of(
                new CommitPipeline("none", "")), TokenUsage.NONE));
    }

    @Test
    void aRedPipelineIsReRunOnceBeforeItIsHandedBack(@TempDir Path root) {
        StateService state = stateWith(root, Deployment.Target.of("shop", "dev").withMerge(SHA));
        redOnTheCode();
        when(pipelines.retryFailed("shop", URL)).thenReturn(new Answer<>(Optional.of("re-run"), TokenUsage.NONE));
        DeployPipelines deploys = new DeployPipelines(pipelines, causes, fixes, CLOCK, new FlowReports(state, CLOCK));

        DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        verify(fixes, never()).open(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        assertThat(state.task("ABC-42").orElseThrow().deployment().targets().getFirst().retriedTip()).isTrue();
    }

    @Test
    void aRedPipelineThatFailedAgainAfterItsOneReRunIsHandedBackWithThePipelineToFix(@TempDir Path root) {
        StateService state = stateWith(root, Deployment.Target.of("shop", "dev").withMerge(SHA).withRetried(SHA));
        redOnTheCode();
        DeployPipelines deploys = new DeployPipelines(pipelines, causes, fixes, CLOCK, new FlowReports(state, CLOCK));

        DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

        assertThat(reading).isEqualTo(new DeployPipelines.Reading(DeployPipelines.Kind.RED,
                "red pipeline on the deploy commit: shop 01234567", "shop", URL, List.of()));
        verify(pipelines, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void aDeployRedOnItsCommitAfterItsReRunStandsOnALaterGreenPipelineContainingIt(@TempDir Path root) {
        StateService state = stateWith(root, Deployment.Target.of("shop", "dev").withMerge(SHA).withRetried(SHA));
        when(pipelines.onCommit("shop", REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(
                new CommitPipeline("failed", URL)), TokenUsage.NONE));
        when(causes.judgeDeploy("ABC-42", "shop", URL))
                .thenReturn(new RedCauses.Verdict(RedCause.CODE, false, "", Optional.empty()));
        when(pipelines.onBranchSince("shop", REMOTE, "dev", SHA)).thenReturn(new Answer<>(Optional.of(
                new CommitPipeline("success", "https://git.example.invalid/shop/web/-/pipelines/9")), TokenUsage.NONE));
        DeployPipelines deploys = new DeployPipelines(pipelines, causes, fixes, CLOCK, new FlowReports(state, CLOCK));

        DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
        verify(fixes, never()).open(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void aRedPipelineIsHandedBackAsItStandsWhereTheHostRefusesTheReRun(@TempDir Path root) {
        StateService state = stateWith(root, Deployment.Target.of("shop", "dev").withMerge(SHA));
        redOnTheCode();
        when(pipelines.retryFailed("shop", URL)).thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1: 403"));
        DeployPipelines deploys = new DeployPipelines(pipelines, causes, fixes, CLOCK, new FlowReports(state, CLOCK));

        DeployPipelines.Reading reading = deploys.read("ABC-42", state.task("ABC-42").orElseThrow());

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.RED);
    }

    @Test
    void aRedPipelineStaysOpenWhenTheWorktreeOfItsFixCannotBeOpened(@TempDir Path root) {
        StateService state = stateWith(root, Deployment.Target.of("shop", "dev").withMerge(SHA).withRetried(SHA));
        redOnTheCode();
        TaskState task = state.task("ABC-42").orElseThrow();
        doThrow(new IllegalStateException("the path holds another checkout")).when(fixes).open(task, "ABC-42", "shop");
        DeployPipelines deploys = new DeployPipelines(pipelines, causes, fixes, CLOCK, new FlowReports(state, CLOCK));

        DeployPipelines.Reading reading = deploys.read("ABC-42", task);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        assertThat(reading.said()).isEqualTo("shop 01234567: pipeline red on the code, its deploy worktree not opened: the path holds another checkout");
    }
}
