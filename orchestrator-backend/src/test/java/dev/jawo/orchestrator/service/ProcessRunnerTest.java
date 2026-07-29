package dev.jawo.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerTest {

    @Test
    void runDetachedReturnsWithoutWaitingForTheProcess() {
        ProcessRunner runner = new ProcessRunner();

        long start = System.currentTimeMillis();
        runner.runDetached(null, List.of("sleep", "3"));
        long elapsedMillis = System.currentTimeMillis() - start;

        assertThat(elapsedMillis).isLessThan(2_000);
    }
}
