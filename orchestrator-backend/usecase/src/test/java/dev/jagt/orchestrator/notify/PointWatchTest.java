package dev.jagt.orchestrator.notify;

import static dev.jagt.orchestrator.notify.TestPointWatchBuilder.pointWatch;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.port.channel.NotificationSink;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.RoleRecord;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PointWatchTest {

  @TempDir Path root;
  private final NotificationSink notifications = mock(NotificationSink.class);
  private final TaskState inProgress =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
  private final TaskState stalled =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
          .stop(new TaskStop(TaskStop.Trigger.STALLED, 7L, "same round"))
          .build();
  private StateService state;
  private PointWatch watch;

  @BeforeEach
  void stateAndWatchTellingNotifications() {
    state = stateService().withRoot(root).build();
    watch = pointWatch().withRoot(root).build(state, notifications);
  }

  @Test
  void tellsNobodyWhatStandsWhenTheFirstReadComes() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/proj/-/merge_requests/7")
            .build());

    watch.sweep();

    verifyNoInteractions(notifications);
  }

  @Test
  void tellsRequestOpeningOnceNamingIt() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    watch.sweep();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("https://host/proj/-/merge_requests/7")
            .build());

    watch.sweep();
    watch.sweep();

    verify(notifications, times(1))
        .send(
            Notification.fromAgent(
                Point.REQUEST_OPENED.id(),
                "ABC-1",
                "request opened",
                "https://host/proj/-/merge_requests/7"));
  }

  @Test
  void tellsDeployLandingOnce() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build());
    watch.sweep();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    watch.sweep();
    watch.sweep();

    verify(notifications, times(1))
        .send(Notification.fromAgent(Point.DEPLOYED.id(), "ABC-1", "deployed", ""));
  }

  @Test
  void tellsPointPersistedWriteAnnouncesWithoutWaitingForSweep() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build());
    watch.sweep();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).build());

    watch.observe(new StateWritten(Map.of(), state.tasks()));

    verify(notifications).send(Notification.fromAgent(Point.DONE.id(), "ABC-1", "done", "done"));
  }

  @Test
  void tellsInstalledRolePassedOnceWhenItsRecordGainsPassedSha() throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    PointWatch installed = pointWatch().withRoot(root).build(state, notifications);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build());
    installed.sweep();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .roles(Map.of("acceptance", RoleRecord.defaults().withPassed("tipA")))
            .build());

    installed.sweep();
    installed.sweep();

    verify(notifications, times(1))
        .send(Notification.fromAgent("acceptance-passed", "ABC-1", "acceptance passed", "tipA"));
  }

  @Test
  void tellsStopTheHumanKeepsToLiftOnce() {
    PointWatch lifting =
        pointWatch()
            .withRoot(root)
            .withProfile(new Arbiter.Profile(MasterMode.ACT, Set.of("lift")))
            .build(state, notifications);
    state.putTask("ABC-1", inProgress);
    lifting.sweep();
    state.putTask("ABC-1", stalled);

    lifting.sweep();

    verify(notifications, times(1))
        .send(Notification.fromAgent("stopped", "ABC-1", "stopped", "STALLED@7"));
  }

  @Test
  void tellsAnAskOncePerStampAndAgainOnTheNextStamp() {
    state.putTask("ABC-1", inProgress);
    watch.sweep();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .session(Session.defaults().withAskedAt(4_000))
            .build());

    watch.sweep();
    watch.sweep();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .session(Session.defaults().withAskedAt(9_000))
            .build());
    watch.sweep();

    verify(notifications, times(1))
        .send(Notification.fromAgent("stopped", "ABC-1", "stopped", "ask@4000"));
    verify(notifications, times(1))
        .send(Notification.fromAgent("stopped", "ABC-1", "stopped", "ask@9000"));
  }

  @Test
  void tellsDeployLandingOnceWhenWriteEventCarriesSnapshotFromBeforeItWasTold() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build());
    watch.sweep();
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    Map<String, TaskState> beforeItWasTold = state.tasks();
    watch.sweep();

    watch.observe(new StateWritten(Map.of(), beforeItWasTold));

    verify(notifications, times(1))
        .send(Notification.fromAgent(Point.DEPLOYED.id(), "ABC-1", "deployed", ""));
  }

  @Test
  void tellsNothingOfStopTheMasterHolds() {
    PointWatch holding =
        pointWatch()
            .withRoot(root)
            .withProfile(new Arbiter.Profile(MasterMode.ACT, Set.of()))
            .build(state, notifications);
    state.putTask("ABC-1", inProgress);
    holding.sweep();
    state.putTask("ABC-1", stalled);

    holding.sweep();

    verifyNoInteractions(notifications);
  }
}
