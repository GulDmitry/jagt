package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StopReadJobTest {

    private final StopReads reads = mock(StopReads.class);

    @Test
    void readsTheChecksOfATaskStoppedByAnOutsideConditionWhateverAutoReviewSays(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).mrUrl("http://mr/1")
                .autoReview(false).stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down")).build());

        new StopReadJob(reads).runOn(state.tasks());

        verify(reads).probe("ABC-42");
    }

    @Test
    void readsTheChecksOfAResolvedDeployConflictHeldByAnInfrastructureStop(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down")).build());

        new StopReadJob(reads).runOn(state.tasks());

        verify(reads).probe("ABC-42");
    }

    @Test
    void readsNothingForAStopOnlyAHumanLifts(@TempDir Path root) {
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).mrUrl("http://mr/1")
                .stop(new TaskStop(TaskStop.Trigger.PACE, 5, "too many moves")).build());

        new StopReadJob(reads).runOn(state.tasks());

        verify(reads, never()).probe("ABC-42");
    }

    private static StateService state(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }
}
