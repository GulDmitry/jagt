package dev.jagt.orchestrator.notify;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.role.CheckerRole;
import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.role.Points;
import dev.jagt.orchestrator.flow.role.RolePointKind;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.notify.PointCatalogue.Standing;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PointCatalogueTest {

  @Test
  void taskWithoutRequestOrDeploymentStandsAtNoPoint() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE)).isEmpty();
  }

  @Test
  void taskWithRequestsStandsAtRequestOpenedKeyedBySortedRequests() {
    TaskState task =
        TaskState.builder(
                List.of(
                    TaskRepo.of("b", "/wb").withMrUrl("https://host/b/7"),
                    TaskRepo.of("a", "/wa").withMrUrl("https://host/a/3")),
                TaskStatus.CI_POLLING)
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(new Standing(Point.REQUEST_OPENED, "https://host/a/3\nhttps://host/b/7"));
  }

  @Test
  void taskWithMergedRequestAlsoStandsAtMerged() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("a", "/wa").withMrUrl("https://host/a/3").withMergedByHost()),
                TaskStatus.CI_POLLING)
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .contains(new Standing(Point.MERGED, "https://host/a/3"));
  }

  @Test
  void approvedRoundStandsAtApprovedKeyedByRequestsAndTip() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .mrUrl("https://host/a/3")
            .approved(true)
            .checksTip("abc")
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .contains(new Standing(Point.APPROVED, "https://host/a/3@abc"));
  }

  @Test
  void pressedDeploymentStandsAtDeployPressedKeyedByPress() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .deployment(new Deployment(5L, List.of(), 0, null))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(new Standing(Point.DEPLOY_PRESSED, "5"));
  }

  @Test
  void unpressedDeploymentStandsAtNoPoint() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .deployment(new Deployment(0L, List.of(), 0, null))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE)).isEmpty();
  }

  @Test
  void deployedTaskStandsAtDeployedKeyedByItsPress() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
            .deployment(new Deployment(5L, List.of(), 0, null))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .contains(new Standing(Point.DEPLOYED, "5"));
  }

  @Test
  void taskInDeployConflictStandsAtDeployConflict() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .deployment(new Deployment(5L, List.of(), 0, null))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .contains(new Standing(Point.DEPLOY_CONFLICT, "5"));
  }

  @Test
  void doneTaskStandsAtDone() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DONE).build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(new Standing(Point.DONE, "done"));
  }

  @Test
  void roleRecordStandsAtItsDeclaredPointsKeyedByShaAndDigests() {
    List<Point> declared =
        Points.all(
            List.of(
                new CheckerRole(
                    "acc",
                    false,
                    List.of(),
                    List.of(
                        RolePointKind.PASSED, RolePointKind.ROUND_SENT, RolePointKind.DECLINED))));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .roles(Map.of("acc", new RoleRecord("", "tipA", "dig1", "rows", "dig2")))
            .build();

    assertThat(PointCatalogue.of(task, declared, StopHold.NONE))
        .extracting(standing -> standing.point().id() + "=" + standing.key())
        .containsExactlyInAnyOrder("acc-passed=tipA", "acc-round-sent=dig1", "acc-declined=dig2");
  }

  @Test
  void roleRecordOfRoleDeclaringNoPointsStandsAtNothing() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .roles(Map.of("acc", new RoleRecord("", "tipA", "", "", "")))
            .build();

    assertThat(PointCatalogue.of(task, Points.all(List.of()), StopHold.NONE)).isEmpty();
  }

  @Test
  void stoppedTaskStandsAtStoppedKeyedByTheStop() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 7L, "same round"))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.HUMAN))
        .containsExactly(new Standing(Point.STOPPED, "STALLED@7"));
  }

  @Test
  void stopTheMasterHoldsStandsAtNothing() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 7L, "same round"))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.ARBITER)).isEmpty();
  }

  @Test
  void pauseTheHumanSetStandsAtNothing() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.PAUSE, 7L, "paused"))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.HUMAN)).isEmpty();
  }

  @Test
  void silentAgentStandsAtStoppedKeyedBySinceWhenNoStopStands() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .session(Session.defaults().withQuietSince(1_000))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(new Standing(Point.STOPPED, "silence@1000"));
  }

  @Test
  void silentAgentThatAskedQuestionStandsAtNothing() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .session(Session.defaults().withQuietSince(1_000))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE)).isEmpty();
  }

  @Test
  void askingSessionStandsAtStoppedKeyedByItsStamp() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .session(Session.defaults().withAskedAt(4_000))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(new Standing(Point.STOPPED, "ask@4000"));
  }

  @Test
  void stopOutranksTheSilenceOnOneTask() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 7L, "same round"))
            .session(Session.defaults().withQuietSince(1_000))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.HUMAN))
        .containsExactly(new Standing(Point.STOPPED, "STALLED@7"));
  }

  @Test
  void elapsedWindowStandsAtAutoReviewKeyedByTheWindow() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .told(ToldOnce.none().with(ToldOnce.Kind.AUTO_REVIEW_WINDOW, "900"))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(new Standing(Point.AUTO_REVIEW, "AUTO_REVIEW_WINDOW=900"));
  }

  @Test
  void unreadableRoundAfterAnElapsedWindowStandsAtAutoReviewUnderNewKey() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .told(
                ToldOnce.none()
                    .with(ToldOnce.Kind.AUTO_REVIEW_WINDOW, "900")
                    .with(ToldOnce.Kind.AUTO_REVIEW_UNREADABLE, "2"))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(
            new Standing(Point.AUTO_REVIEW, "AUTO_REVIEW_WINDOW=900;AUTO_REVIEW_UNREADABLE=2"));
  }

  @Test
  void goneRequestStandsAtAutoReviewKeyedByTheRequest() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
            .told(ToldOnce.none().with(ToldOnce.Kind.CONFLICT_REQUEST_GONE, "https://host/a/3"))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .containsExactly(new Standing(Point.AUTO_REVIEW, "CONFLICT_REQUEST_GONE=https://host/a/3"));
  }

  @Test
  void conflictTheHumanWasToldOfStandsAtConflictStalledKeyedByWhenTold() {
    Deployment.Target target =
        new Deployment.Target(
            "proj",
            "dev",
            Deployment.Target.Landed.defaults(),
            null,
            Deployment.Target.Progress.defaults()
                .withConflict(Conflict.of(Deployment.Cause.MERGE, 1_000).toldAt(2_000, "x")));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .deployment(new Deployment(5L, List.of(target), 0, null))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .contains(new Standing(Point.CONFLICT_STALLED, "2000"));
  }

  @Test
  void conflictNobodyWasToldOfStandsAtNoStall() {
    Deployment.Target target =
        new Deployment.Target(
            "proj",
            "dev",
            Deployment.Target.Landed.defaults(),
            null,
            Deployment.Target.Progress.defaults()
                .withConflict(Conflict.of(Deployment.Cause.MERGE, 1_000)));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .deployment(new Deployment(5L, List.of(target), 0, null))
            .build();

    assertThat(PointCatalogue.of(task, List.of(), StopHold.NONE))
        .noneMatch(standing -> standing.point().equals(Point.CONFLICT_STALLED));
  }
}
