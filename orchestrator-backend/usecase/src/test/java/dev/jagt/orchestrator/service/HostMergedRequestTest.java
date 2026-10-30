package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostMergedRequestTest {

    private final ReviewReader reviewReader = mock(ReviewReader.class);

    @Test
    void aSquashMergedRequestIsSeenMergedByTheHostsWord(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
                .alias("a1").mrUrl("http://mr/1").build());
        ConfigService config = mock(ConfigService.class);
        WorktreeInspection inspection = mock(WorktreeInspection.class);
        RoundReading reading = new RoundReading(state, reviewReader,
                new MergedRequests(state, config, inspection, new WorktreeChanges(config, inspection)));
        when(reviewReader.read("ABC-1", "http://mr/1")).thenReturn(new Answer<>(Optional.of(
                new ReviewFacts(true, false, "success", "", List.of(), 0, List.of(), List.of(), "", true, false)),
                TokenUsage.NONE));

        reading.read("ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().requestsMerged()).isTrue();
    }
}
