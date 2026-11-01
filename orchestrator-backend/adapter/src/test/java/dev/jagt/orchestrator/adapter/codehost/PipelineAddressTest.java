package dev.jagt.orchestrator.adapter.codehost;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineAddressTest {

    @Test
    void readsNoPipelineFromAnAddressNamingNoProject() {
        assertThat(PipelineAddress.parse("https://gitlab.example/pipelines/9")).isEmpty();
    }

    @Test
    void readsTheProjectAndIdOfAGitLabPipeline() {
        var address = PipelineAddress.parse("https://gitlab.example/acme/app/-/pipelines/9").orElseThrow();

        assertThat(address.project()).isEqualTo("acme/app");
        assertThat(address.id()).isEqualTo("9");
    }
}
