package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.FailedJob;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayCodeHostTest {

    private final ReplayCodeHost replay = new ReplayCodeHost();
    private final HostSettings host = new HostSettings("proj", "orchestrator.codeHost", null, Path.of("/repo"),
            List.of(), List.of(), List.of(), List.of());

    @Test
    void aReplayedFailureReadsAsTheCodesSoTheSessionHearsOfIt() {
        Answer<List<FailedJob>> answer = replay.failedJobs(host, "https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).contains(List.of());
    }

    @Test
    void aReplayedRunSendsNothingToAHost() {
        Answer<String> answer = replay.retryFailed(host, "https://git.example.invalid/shop/web/-/pipelines/77");

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void aReplayedRunReadsEveryCommitAsGreenSoItsDeploysAreChecked() {
        assertThat(replay.onCommit(host, "/tmp/shop.git", "0123abc").facts().orElseThrow().status()).isEqualTo("success");
    }

    @Test
    void aReplayedRunClosesNoRequest() {
        assertThat(replay.closeRequest(host, "https://git.example.invalid/shop/web/-/merge_requests/7").facts()).isEmpty();
    }
}
