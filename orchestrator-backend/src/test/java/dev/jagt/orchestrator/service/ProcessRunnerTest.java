package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerTest {

    @Test
    void aLaunchedAppSurvivesTheCtrlCThatStopsTheBackend() throws Exception {
        Process launched = new ProcessRunner().runDetached(null, List.of("sleep", "5"));

        new ProcessBuilder("kill", "-INT", String.valueOf(launched.pid())).start().waitFor();

        assertThat(launched.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
        launched.destroyForcibly();
    }

    @Test
    void runDetachedReturnsWithoutWaitingForTheProcess() {
        ProcessRunner runner = new ProcessRunner();

        long start = System.currentTimeMillis();
        runner.runDetached(null, List.of("sleep", "3"));
        long elapsedMillis = System.currentTimeMillis() - start;

        assertThat(elapsedMillis).isLessThan(2_000);
    }
}
