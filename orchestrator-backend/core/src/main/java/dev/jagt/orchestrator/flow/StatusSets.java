package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.task.TaskStatus.APPROVED;
import static dev.jagt.orchestrator.task.TaskStatus.CI_FAILED;
import static dev.jagt.orchestrator.task.TaskStatus.CI_POLLING;
import static dev.jagt.orchestrator.task.TaskStatus.DEPLOYED;
import static dev.jagt.orchestrator.task.TaskStatus.DEPLOY_CONFLICT;
import static dev.jagt.orchestrator.task.TaskStatus.DONE;
import static dev.jagt.orchestrator.task.TaskStatus.IN_PROGRESS;
import static dev.jagt.orchestrator.task.TaskStatus.NEW;
import static dev.jagt.orchestrator.task.TaskStatus.PLAN_PENDING;
import static dev.jagt.orchestrator.task.TaskStatus.REVERTED;
import static dev.jagt.orchestrator.task.TaskStatus.REVIEWED;
import static dev.jagt.orchestrator.task.TaskStatus.REVIEW_PENDING;
import static dev.jagt.orchestrator.task.TaskStatus.SHIPPING;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.EnumSet;
import java.util.Set;

/** The statuses each rule of the chart names, and the predicates over them. */
final class StatusSets {

  private StatusSets() {}

  static final Set<TaskStatus> OPEN = EnumSet.complementOf(EnumSet.of(DONE));

  static final Set<TaskStatus> HELD = EnumSet.of(REVERTED, DEPLOY_CONFLICT);

  static final Set<TaskStatus> REPORTED =
      EnumSet.of(PLAN_PENDING, IN_PROGRESS, SHIPPING, REVIEW_PENDING, CI_FAILED, CI_POLLING);

  static final Set<TaskStatus> BEFORE_THE_CODE = EnumSet.of(NEW, PLAN_PENDING, IN_PROGRESS);

  static final Set<TaskStatus> BEFORE_THE_VERDICT =
      EnumSet.of(NEW, IN_PROGRESS, SHIPPING, REVIEW_PENDING, CI_POLLING, CI_FAILED);

  static final Set<TaskStatus> WAITING_FOR_A_DEPLOY = EnumSet.of(REVIEWED, APPROVED);

  static final Set<TaskStatus> WAITING_ON_THE_HOST = union(WAITING_FOR_A_DEPLOY, CI_POLLING);

  static final Set<TaskStatus> READ = union(WAITING_FOR_A_DEPLOY, CI_FAILED);

  static final Set<TaskStatus> READ_FROM = union(WAITING_ON_THE_HOST, CI_FAILED);

  static final Set<TaskStatus> HANDED_OVER = union(WAITING_FOR_A_DEPLOY, DEPLOYED);

  static final Set<TaskStatus> WATCHED = EnumSet.of(NEW, IN_PROGRESS, SHIPPING);

  static final Set<TaskStatus> READ_BY_THE_ARBITER = EnumSet.of(PLAN_PENDING, REVIEW_PENDING);

  static final Set<TaskStatus> OUT_FOR_REVIEW = EnumSet.of(CI_POLLING, REVIEWED);

  static final Set<TaskStatus> HANDED_BACK = EnumSet.of(REVIEW_PENDING, CI_FAILED);

  static final Set<TaskStatus> AWAITING_ITS_INSTRUCTION = EnumSet.of(SHIPPING, DEPLOY_CONFLICT);

  static final Set<AgentReport> STANDING_THROUGH_A_READ =
      EnumSet.of(AgentReport.QUESTION, AgentReport.PERMISSION);

  static boolean reportable(TaskStatus status) {
    return REPORTED.contains(status);
  }

  static boolean holds(TaskStatus status) {
    return HELD.contains(status);
  }

  static boolean standsThroughRead(AgentReport report) {
    return STANDING_THROUGH_A_READ.contains(report);
  }

  static Set<TaskStatus> reads() {
    return EnumSet.copyOf(READ);
  }

  static boolean watched(TaskStatus status) {
    return WATCHED.contains(status);
  }

  static boolean readByTheArbiter(TaskStatus status) {
    return READ_BY_THE_ARBITER.contains(status);
  }

  static boolean outForReview(TaskStatus status) {
    return OUT_FOR_REVIEW.contains(status);
  }

  static boolean handedBack(TaskStatus status) {
    return HANDED_BACK.contains(status);
  }

  static boolean awaitsItsInstruction(TaskStatus status) {
    return AWAITING_ITS_INSTRUCTION.contains(status);
  }

  static boolean waitsForDeploy(TaskStatus status) {
    return WAITING_FOR_A_DEPLOY.contains(status);
  }

  static boolean waitsOnTheHost(TaskStatus status) {
    return WAITING_ON_THE_HOST.contains(status);
  }

  static boolean handedOver(TaskStatus status) {
    return HANDED_OVER.contains(status);
  }

  private static Set<TaskStatus> union(Set<TaskStatus> base, TaskStatus extra) {
    Set<TaskStatus> all = EnumSet.copyOf(base);
    all.add(extra);
    return all;
  }
}
