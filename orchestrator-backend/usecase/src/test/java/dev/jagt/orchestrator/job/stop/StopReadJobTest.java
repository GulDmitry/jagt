package dev.jagt.orchestrator.job.stop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
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
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.testing.FakeHost;
import dev.jagt.orchestrator.testing.FakeJobFaults;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StopReadJobTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});

  @TempDir Path root;
  private final StopReads reads = mock(StopReads.class);
  private StateService state;

  @BeforeEach
  void stateOnDisk() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()),
            reducer,
            event -> {});
  }

  @Test
  void readsTheChecksOfTaskStoppedByAnOutsideConditionWhateverAutoReviewSays() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .autoReview(false)
            .stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down"))
            .build());

    new StopReadJob(reads, new FakeJobFaults()).runOn(state.tasks());

    verify(reads).probe("ABC-42");
  }

  @Test
  void readsTheChecksOfResolvedDeployConflictHeldByAnInfrastructureStop() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down"))
            .build());

    new StopReadJob(reads, new FakeJobFaults()).runOn(state.tasks());

    verify(reads).probe("ABC-42");
  }

  @Test
  void readsNothingForStopOnlyHumanLifts() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .stop(new TaskStop(TaskStop.Trigger.PACE, 5, "too many moves"))
            .build());

    new StopReadJob(reads, new FakeJobFaults()).runOn(state.tasks());

    verify(reads, never()).probe("ABC-42");
  }

  @Test
  void readThatThrewIsReportedAsItsJobsFailure() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .stop(new TaskStop(TaskStop.Trigger.INFRA, 5, "host down"))
            .build());
    doThrow(new IllegalStateException("host unreachable")).when(reads).probe("ABC-42");
    FakeJobFaults faults = new FakeJobFaults();

    new StopReadJob(reads, faults).runOn(state.tasks());

    assertThat(faults.causes()).containsExactly("host unreachable");
  }
}
