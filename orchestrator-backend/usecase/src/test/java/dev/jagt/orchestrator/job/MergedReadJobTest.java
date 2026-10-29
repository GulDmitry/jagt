package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.WorktreeInspection;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MergedReadJobTest {

    @Test
    void taskIsShippedAndPolledAgainWhenTheBaseWasResetUnderItsMergedRequest(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("http://mr/1").build()
                .withMerged("proj", true));
        ConfigService config = mock(ConfigService.class);
        WorktreeInspection inspection = mock(WorktreeInspection.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/srv/proj", "origin/main", "dev", List.of()));
        when(inspection.mergedIntoTarget(any(), any(), any())).thenReturn(false);

        new MergedReadJob(state, new MergedRequests(state, config, inspection)).run();

        assertThat(state.task("ABC-42").orElseThrow().requestsMerged()).isFalse();
    }
}
