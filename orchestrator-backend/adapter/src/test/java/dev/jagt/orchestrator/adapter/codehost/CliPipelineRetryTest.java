package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.FailedJob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CliPipelineRetryTest {

    private final Processes processes = mock(Processes.class);
    private final CliPipelineRetry retry = new CliPipelineRetry(processes);

    @Test
    void aGitLabPipelineIsRetriedThroughItsRetryEndpoint() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "{\"status\":\"pending\"}", ""));

        Answer<String> answer = retry.retryFailed("https://git.example.invalid/shop/web/-/pipelines/77");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("api", "--hostname", "git.example.invalid", "-X", "POST",
                "projects/shop%2Fweb/pipelines/77/retry");
        assertThat(answer.facts()).isPresent();
    }

    @Test
    void aGitHubRunReRunsOnlyItsFailedJobs() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", "Requested rerun of run 5"));

        retry.retryFailed("https://code.example.invalid/acme/shop/actions/runs/5");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("run", "rerun", "5", "--failed", "-R", "acme/shop");
    }

    @Test
    void noCliOnPathIsUnavailableNamingIt() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenThrow(new IllegalStateException("Failed to start command: glab"));

        Answer<String> answer = retry.retryFailed("https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).startsWith("glab did not run");
    }

    @Test
    void aCliNotLoggedInIsUnavailableNamingIt() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "glab: 401 Unauthorized"));

        Answer<String> answer = retry.retryFailed("https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("glab not logged in to git.example.invalid");
    }

    @Test
    void aGitLabJobCarriesTheReasonTheHostGivesForItsFailure() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"id\":9,\"name\":\"unit-tests\",\"failure_reason\":\"runner_system_failure\"}]", ""));

        Answer<List<FailedJob>> answer = retry.failedJobs("https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).contains(List.of(new FailedJob("9", "unit-tests", "runner_system_failure")));
    }

    @Test
    void aGitHubRunThatNeverStartedFailsOnInfrastructure() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "{\"conclusion\":\"startup_failure\",\"jobs\":[]}", ""));

        Answer<List<FailedJob>> answer = retry.failedJobs("https://code.example.invalid/acme/shop/actions/runs/5");

        assertThat(answer.facts()).contains(List.of(new FailedJob("5", "workflow", "startup_failure")));
    }

    @Test
    void aGitHubJobThatFailedItsStepsFailsForTheCode() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "{\"conclusion\":\"failure\",\"jobs\":[{\"databaseId\":3,\"name\":\"build\",\"conclusion\":\"failure\"},"
                        + "{\"databaseId\":4,\"name\":\"lint\",\"conclusion\":\"success\"}]}", ""));

        Answer<List<FailedJob>> answer = retry.failedJobs("https://code.example.invalid/acme/shop/actions/runs/5");

        assertThat(answer.facts()).contains(List.of(new FailedJob("3", "build", "failure")));
    }

    @Test
    void anAddressThatIsNoPipelineIsRefusedWithoutRunningAnything() {
        Answer<String> answer = retry.retryFailed("https://git.example.invalid/shop/web/-/merge_requests/4");

        assertThat(answer.failure()).startsWith("not the address of a pipeline");
    }

    @Test
    void aGitLabCommitIsReadThroughItsNewestPipeline() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"status\":\"failed\",\"web_url\":\"https://git.example.invalid/shop/web/-/pipelines/78\"}]", ""));

        Answer<CommitPipeline> answer = retry.onCommit("git@git.example.invalid:shop/web.git", "0123abc");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("api", "--hostname", "git.example.invalid",
                "projects/shop%2Fweb/pipelines?sha=0123abc&per_page=1");
        assertThat(answer.facts()).contains(new CommitPipeline("failed",
                "https://git.example.invalid/shop/web/-/pipelines/78"));
    }

    @Test
    void aCommitTheHostRanNoPipelineForReadsAsNone() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "[]", ""));

        Answer<CommitPipeline> answer = retry.onCommit("https://git.example.invalid/shop/web.git", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
    }

    @Test
    void aGitHubCommitReadsAsItsWorstRun() {
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "[{\"status\":\"completed\",\"conclusion\":\"success\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/1\"},"
                        + "{\"status\":\"in_progress\",\"conclusion\":\"\",\"url\":\"https://github.example.invalid/acme/shop/actions/runs/2\"}]", ""));

        Answer<CommitPipeline> answer = retry.onCommit("https://github.example.invalid/acme/shop.git", "0123abc");

        assertThat(answer.facts()).contains(new CommitPipeline("in_progress",
                "https://github.example.invalid/acme/shop/actions/runs/2"));
    }

    @Test
    void aCommitWhosePipelineCannotBeReadSaysWhatStoppedTheRead() {
        when(processes.run(isNull(), any(Duration.class), anyList()))
                .thenReturn(new Processes.Result(1, "", "glab: 401 Unauthorized"));

        Answer<CommitPipeline> answer = retry.onCommit("https://git.example.invalid/shop/web.git", "0123abc");

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("glab not logged in to git.example.invalid");
    }
}
