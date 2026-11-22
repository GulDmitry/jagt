package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The points a task stands at, each with the key that tells one standing from the next. */
final class PointCatalogue {

  record Standing(Point point, String key) {}

  private PointCatalogue() {}

  static List<Standing> of(TaskState task, List<Point> declared, StopHold hold) {
    List<Standing> standing = new ArrayList<>();
    rolePoints(task, declared, standing);
    stopped(task, hold, standing);
    autoReview(task, standing);
    conflictStalled(task, standing);
    String requests = String.join("\n", task.requests().stream().sorted().toList());
    if (!requests.isEmpty()) {
      standing.add(new Standing(Point.REQUEST_OPENED, requests));
      if (task.requestsMerged()) {
        standing.add(new Standing(Point.MERGED, requests));
      }
      if (Boolean.TRUE.equals(task.approved())) {
        standing.add(new Standing(Point.APPROVED, requests + "@" + task.checksTip()));
      }
    }
    Deployment deployment = task.deployment();
    String pressed = deployment == null ? "" : String.valueOf(deployment.pressedAt());
    if (deployment != null && deployment.pressedAt() > 0) {
      standing.add(new Standing(Point.DEPLOY_PRESSED, pressed));
    }
    if (task.status() == TaskStatus.DEPLOYED) {
      standing.add(new Standing(Point.DEPLOYED, pressed));
    }
    if (task.status() == TaskStatus.DEPLOY_CONFLICT) {
      standing.add(new Standing(Point.DEPLOY_CONFLICT, pressed));
    }
    if (task.status() == TaskStatus.DONE) {
      standing.add(new Standing(Point.DONE, "done"));
    }
    return standing;
  }

  private static void stopped(TaskState task, StopHold hold, List<Standing> standing) {
    TaskStop stop = task.stop();
    if (stop != null
        && stop.trigger().ends() != TaskStop.Trigger.Ends.PAUSE
        && hold != StopHold.ARBITER) {
      standing.add(new Standing(Point.STOPPED, stop.key()));
      return;
    }
    long asked = task.session().askedAt();
    if (asked > 0) {
      standing.add(new Standing(Point.STOPPED, "ask@" + asked));
      return;
    }
    long since = SessionPhase.silentSinceUnasked(task);
    if (since > 0) {
      standing.add(new Standing(Point.STOPPED, "silence@" + since));
    }
  }

  private static void autoReview(TaskState task, List<Standing> standing) {
    ToldOnce told = task.toldOrNone();
    String key =
        Stream.of(
                ToldOnce.Kind.AUTO_REVIEW_WINDOW,
                ToldOnce.Kind.AUTO_REVIEW_UNREADABLE,
                ToldOnce.Kind.CONFLICT_REQUEST_GONE)
            .filter(told::has)
            .map(kind -> kind + "=" + told.valueOf(kind))
            .collect(Collectors.joining(";"));
    if (!key.isEmpty()) {
      standing.add(new Standing(Point.AUTO_REVIEW, key));
    }
  }

  private static void conflictStalled(TaskState task, List<Standing> standing) {
    task.currentConflict()
        .map(Conflict::told)
        .ifPresent(
            told -> standing.add(new Standing(Point.CONFLICT_STALLED, String.valueOf(told.at()))));
  }

  private static void rolePoints(TaskState task, List<Point> declared, List<Standing> standing) {
    Map<String, Point> byId = new HashMap<>();
    declared.forEach(point -> byId.put(point.id(), point));
    task.roles()
        .forEach((role, record) -> {
          roleStanding(byId, role + "-passed", record.passed(), standing);
          roleStanding(byId, role + "-round-sent", record.roundDigest(), standing);
          roleStanding(byId, role + "-declined", record.declined(), standing);
        });
  }

  private static void roleStanding(
      Map<String, Point> byId, String id, String key, List<Standing> standing) {
    if (byId.containsKey(id) && !key.isEmpty()) {
      standing.add(new Standing(byId.get(id), key));
    }
  }
}
