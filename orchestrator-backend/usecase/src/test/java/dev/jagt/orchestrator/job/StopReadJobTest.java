package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StopReadJobTest {

    private final ConfigService config = mock(ConfigService.class);
    private final StopReads reads = mock(StopReads.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(10_000_000), ZoneOffset.UTC);

    @Test
    void readsTheChecksOfATaskStoppedByAnOutsideCondition(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).mrUrl("http://mr/1")
                .mrCreatedAt(9_000_000).stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down")).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withAutoReview(ConfigService.ConfigFile.AutoReviewConfig.defaults().withEnabled(true)));

        new StopReadJob(state, config, reads, clock).run();

        verify(reads).probe("ABC-42");
    }

    @Test
    void readsNothingForAStopOnlyAHumanLifts(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).mrUrl("http://mr/1")
                .mrCreatedAt(9_000_000).stop(new TaskStop(TaskStop.Trigger.PACE, 5, "too many moves")).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withAutoReview(ConfigService.ConfigFile.AutoReviewConfig.defaults().withEnabled(true)));

        new StopReadJob(state, config, reads, clock).run();

        verify(reads, never()).probe("ABC-42");
    }

    private static StateService state(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }
}
