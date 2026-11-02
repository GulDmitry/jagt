package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoCodeHostTest {

    private final NoCodeHost none = new NoCodeHost();
    private final HostSettings host = new HostSettings("shop", "orchestrator.codeHost", null, Path.of("/repo"),
            List.of(), List.of());

    @Test
    void noPipelineIsReadAndTheDeploySettlesOnTheAddressNoHostHas() {
        Answer<CommitPipeline> answer = none.onCommit(host, "git@git.example.invalid:shop/web.git", "0123abc");

        assertThat(answer.facts().orElseThrow().hosted()).isFalse();
    }

    @Test
    void nothingIsReRun() {
        assertThat(none.retryFailed(host, "https://git.example.invalid/shop/web/-/pipelines/7").facts()).isEmpty();
    }
}
