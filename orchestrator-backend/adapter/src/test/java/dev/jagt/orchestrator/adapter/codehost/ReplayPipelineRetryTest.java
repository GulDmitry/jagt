package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.FailedJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayPipelineRetryTest {

    private final ReplayPipelineRetry replay = new ReplayPipelineRetry();

    @Test
    void aReplayedFailureReadsAsTheCodesSoTheSessionHearsOfIt() {
        Answer<List<FailedJob>> answer = replay.failedJobs("https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).contains(List.of());
    }

    @Test
    void aReplayedRunSendsNothingToAHost() {
        Answer<String> answer = replay.retryFailed("https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void aReplayedRunReadsEveryCommitAsGreenSoItsDeploysAreChecked() {
        assertThat(replay.onCommit("/tmp/shop.git", "0123abc").facts().orElseThrow().status()).isEqualTo("success");
    }
}
