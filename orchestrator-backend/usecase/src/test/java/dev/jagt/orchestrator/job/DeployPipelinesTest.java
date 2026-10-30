package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployPipelinesTest {

    private static final String REMOTE = "https://git.example.invalid/shop/web.git";
    private static final String SHA = "0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final PipelineRetry pipelines = mock(PipelineRetry.class);
    private final RedCauses causes = mock(RedCauses.class);
    private final DeployFixWorktrees fixes = mock(DeployFixWorktrees.class);
    private final DeployPipelines deploys = new DeployPipelines(pipelines, causes, fixes, Clock.fixed(NOW, ZoneOffset.UTC));
    private final TaskState deployed = TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null, SHA)),
            TaskStatus.DEPLOYED).build();

    @Test
    void aDeployStandsOnceThePipelineOnItsCommitIsGreen() {
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(
                new CommitPipeline("success", "https://git.example.invalid/shop/web/-/pipelines/7")), TokenUsage.NONE));

        DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
        verify(causes).deployStands("ABC-42");
    }

    @Test
    void aDeployStaysOpenWhileThePipelineOnItsCommitIsRunning() {
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(
                new CommitPipeline("running", "https://git.example.invalid/shop/web/-/pipelines/7")), TokenUsage.NONE));

        DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        assertThat(reading.said()).isEqualTo("shop 01234567: pipeline running");
    }

    @Test
    void aDeployStaysOpenWhereTheHostCannotBeRead() {
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(Answer.failed(TokenUsage.NONE, "glab not logged in to git.example.invalid"));

        DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        assertThat(reading.said()).isEqualTo("shop 01234567: pipeline unread, glab not logged in to git.example.invalid");
    }

    @Test
    void aDeployWhosePipelineIsRedOnTheCodeReadsRedWithThePipelineToFix() {
        String url = "https://git.example.invalid/shop/web/-/pipelines/7";
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(new CommitPipeline("failed", url)),
                TokenUsage.NONE));
        when(causes.judgeDeploy("ABC-42", url)).thenReturn(new RedCauses.Verdict(RedCause.CODE, false, "", Optional.empty()));

        DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

        assertThat(reading).isEqualTo(new DeployPipelines.Reading(DeployPipelines.Kind.RED,
                "red pipeline on the deploy commit: shop 01234567", "shop", url));
        verify(fixes).open(deployed, "ABC-42", "shop");
    }

    @Test
    void aDeployStaysOpenWhenTheWorktreeOfItsFixCannotBeOpened() {
        String url = "https://git.example.invalid/shop/web/-/pipelines/7";
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(new CommitPipeline("failed", url)),
                TokenUsage.NONE));
        when(causes.judgeDeploy("ABC-42", url)).thenReturn(new RedCauses.Verdict(RedCause.CODE, false, "", Optional.empty()));
        doThrow(new IllegalStateException("the path holds another checkout")).when(fixes).open(deployed, "ABC-42", "shop");

        DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        assertThat(reading.said()).isEqualTo("shop 01234567: pipeline red on the code, its deploy worktree not opened: the path holds another checkout");
    }

    @Test
    void aDeployWhosePipelineDiedOnInfrastructureWaitsForItsRetry() {
        String url = "https://git.example.invalid/shop/web/-/pipelines/7";
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(new CommitPipeline("failed", url)),
                TokenUsage.NONE));
        when(causes.judgeDeploy("ABC-42", url)).thenReturn(new RedCauses.Verdict(RedCause.INFRASTRUCTURE, true,
                "runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC", Optional.empty()));

        DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        assertThat(reading.said()).isEqualTo("shop 01234567: pipeline red on infrastructure, "
                + "runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC");
    }

    @Test
    void aDeployIsReadOnTheFixPushedAfterItsMergeNotOnTheMerge() {
        TaskState fixed = deployed.withDeployFix("shop", "f1x0123456789");
        when(pipelines.onCommit(REMOTE, "f1x0123456789")).thenReturn(new Answer<>(Optional.of(
                new CommitPipeline("success", "")), TokenUsage.NONE));

        DeployPipelines.Reading reading = deploys.read("ABC-42", fixed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    }

    @Test
    void aDeployStaysOpenWhileTheHostListsNoPipelineWithinTheGraceAfterThePush() {
        TaskState pushed = TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null, SHA)),
                TaskStatus.DEPLOYED).lastActiveTimestamp(NOW.minusSeconds(9 * 60).toEpochMilli()).build();
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")),
                TokenUsage.NONE));

        DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        assertThat(reading.said()).isEqualTo("shop 01234567: pipeline not started");
    }

    @Test
    void aDeployStandsOnceTheHostStillListsNoPipelineAfterTheGrace() {
        TaskState pushed = TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null, SHA)),
                TaskStatus.DEPLOYED).lastActiveTimestamp(NOW.minusSeconds(10 * 60).toEpochMilli()).build();
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")),
                TokenUsage.NONE));

        DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    }

    @Test
    void aDeployStandsWhenTheHostListsNoPipelineTenMinutesAfterThePushWhateverTheStatusSince() {
        Deployment recorded = Deployment.defaults().withTargets(List.of(
                Deployment.Target.of("shop", "dev").withPushedAt(NOW.minusSeconds(11 * 60).toEpochMilli())));
        TaskState pushed = TaskState.builder(List.of(new TaskRepo("shop", "/wt", REMOTE, null, SHA)),
                TaskStatus.DEPLOYED).lastActiveTimestamp(NOW.minusSeconds(60).toEpochMilli()).deployment(recorded).build();
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(new CommitPipeline("none", "")),
                TokenUsage.NONE));

        DeployPipelines.Reading reading = deploys.read("ABC-42", pushed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    }

    @Test
    void aDeployStandsWhereTheRepositoryHasNoCodeHostAddress() {
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(new Answer<>(Optional.of(CommitPipeline.unhosted()),
                TokenUsage.NONE));

        DeployPipelines.Reading reading = deploys.read("ABC-42", deployed);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    }

    @Test
    void aDeployStandsWhereNoRemoteWasRecordedForTheRepository() {
        TaskState unrecorded = TaskState.builder(List.of(new TaskRepo("shop", "/wt", null, null, SHA)),
                TaskStatus.DEPLOYED).build();

        DeployPipelines.Reading reading = deploys.read("ABC-42", unrecorded);

        assertThat(reading.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    }

    @Test
    void aDeployStaysOpenWhileTheHostDoesNotAnswerAndStandsOnceItDoes() {
        when(pipelines.onCommit(REMOTE, SHA)).thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1"),
                new Answer<>(Optional.of(new CommitPipeline("success", "")), TokenUsage.NONE));

        DeployPipelines.Reading unanswered = deploys.read("ABC-42", deployed);
        DeployPipelines.Reading answered = deploys.read("ABC-42", deployed);

        assertThat(unanswered.kind()).isEqualTo(DeployPipelines.Kind.WAITING);
        assertThat(answered.kind()).isEqualTo(DeployPipelines.Kind.GREEN);
    }
}
