package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StopLogTest {

    @Test
    void appendsOneLineWithEveryFieldBesideTheStateFile(@TempDir Path root) throws IOException {
        StopLog log = new StopLog(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));

        log.record(new StopLog.Stop(100, "ABC-42", "cart", "STALLED", TaskStatus.CI_POLLING, "3 rounds"));

        assertThat(Files.readString(root.resolve("stops.jsonl"))).isEqualTo(
                "{\"at\":100,\"task\":\"ABC-42\",\"alias\":\"cart\",\"trigger\":\"STALLED\","
                        + "\"status\":\"CI_POLLING\",\"evidence\":\"3 rounds\"}\n");
    }
}
