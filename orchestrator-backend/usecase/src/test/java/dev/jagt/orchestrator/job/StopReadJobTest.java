package dev.jagt.orchestrator.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StopReadJobTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()));

  private final StopReads reads = mock(StopReads.class);

  @Test
  void readsTheChecksOfTaskStoppedByAnOutsideConditionWhateverAutoReviewSays(@TempDir Path root) {
    StateService state = state(root);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .autoReview(false)
            .stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down"))
            .build());

    new StopReadJob(reads).runOn(state.tasks());

    verify(reads).probe("ABC-42");
  }

  @Test
  void readsTheChecksOfResolvedDeployConflictHeldByAnInfrastructureStop(@TempDir Path root) {
    StateService state = state(root);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down"))
            .build());

    new StopReadJob(reads).runOn(state.tasks());

    verify(reads).probe("ABC-42");
  }

  @Test
  void readsNothingForStopOnlyHumanLifts(@TempDir Path root) {
    StateService state = state(root);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .stop(new TaskStop(TaskStop.Trigger.PACE, 5, "too many moves"))
            .build());

    new StopReadJob(reads).runOn(state.tasks());

    verify(reads, never()).probe("ABC-42");
  }

  private static StateService state(Path root) {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString())),
                reducer);
  }
}
