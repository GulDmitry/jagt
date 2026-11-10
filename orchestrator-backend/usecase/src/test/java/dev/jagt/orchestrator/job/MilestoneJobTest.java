package dev.jagt.orchestrator.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MilestoneJobTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()));

  private final Notifications notifications = mock(Notifications.class);

  private StateService state(Path root) {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString())),
                reducer);
  }

  @Test
  void tellsYouWhenTasksRequestOpensAndNamesIt(@TempDir Path root) {
    StateService state = state(root);
    MilestoneJob job = new MilestoneJob(state, notifications);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    job.run();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/proj/-/merge_requests/7")
            .build());

    job.run();

    verify(notifications)
        .send(Notification.requestOpened("ABC-1", "https://host/proj/-/merge_requests/7"));
  }

  @Test
  void tellsYouWhenDeployLands(@TempDir Path root) {
    StateService state = state(root);
    MilestoneJob job = new MilestoneJob(state, notifications);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build());
    job.run();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    job.run();

    verify(notifications).send(Notification.deployed("ABC-1", "deployed"));
  }

  @Test
  void tellsNobodyOfTaskAlreadyOutForReviewWhenJagtStarts(@TempDir Path root) {
    StateService state = state(root);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    new MilestoneJob(state, notifications).run();

    verifyNoInteractions(notifications);
  }

  @Test
  void tellsOnceWhileTheStatusStands(@TempDir Path root) {
    StateService state = state(root);
    MilestoneJob job = new MilestoneJob(state, notifications);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    job.run();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());

    job.run();
    job.run();

    verify(notifications, times(1)).send(Notification.requestOpened("ABC-1", ""));
  }

  @Test
  void tellsNothingOfStatusThatIsNeitherRequestNorDeploy(@TempDir Path root) {
    StateService state = state(root);
    MilestoneJob job = new MilestoneJob(state, notifications);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    job.run();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());

    job.run();

    verifyNoInteractions(notifications);
  }

  @Test
  void tellsAgainWhenTaskReturnsToItsRequestAfterLeavingIt(@TempDir Path root) {
    StateService state = state(root);
    MilestoneJob job = new MilestoneJob(state, notifications);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    job.run();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    job.run();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    job.run();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());

    job.run();

    verify(notifications, times(2)).send(Notification.requestOpened("ABC-1", ""));
  }

  @Test
  void staysSilentForTaskThatReappearsAtItsRequestAfterItWasRemoved(@TempDir Path root) {
    StateService state = state(root);
    MilestoneJob job = new MilestoneJob(state, notifications);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    job.run();
    state.removeTask("ABC-1");
    job.run();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());

    job.run();

    verifyNoInteractions(notifications);
  }
}
