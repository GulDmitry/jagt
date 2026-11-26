package dev.jagt.orchestrator.task.master;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MasterRightTest {

  @ParameterizedTest
  @CsvSource({"deploy,DEPLOY", "' Reply ',REPLY", "PLAN,PLAN", "replan,REPLAN", "repo,REPO"})
  void readsMasterRightByItsWord(String word, MasterRight right) {
    assertThat(MasterRight.of(word)).contains(right);
  }

  @ParameterizedTest
  @CsvSource({
    "ACT, none, SHIP, true",
    "ACT, none, REPLY, true",
    "ACT, reply, SHIP, true",
    "ACT, reply, REPLY, false",
    "ACT, ship, SHIP, false",
    "ACT, ship, REPLY, true",
    "JUDGE, none, SHIP, false",
    "JUDGE, none, REPLY, false",
    "OFF, none, SHIP, false",
    "OFF, none, REPLY, false"
  })
  void masterMayShipAndReplyOnlyInActAndWhereTheHumanKeptNeither(
      MasterMode mode, String mine, MasterRight right, boolean may) {
    assertThat(new Arbiter.Profile(mode, Set.of(mine)).may(right)).isEqualTo(may);
  }

  @Test
  void refusesMasterRightNoneOfTheWordsMean() {
    assertThat(MasterRight.of("sometimes")).isEmpty();
    assertThat(MasterRight.of(null)).isEmpty();
    assertThat(MasterRight.ids())
        .containsExactly(
            "ship", "deploy", "revert", "reply", "answer", "plan", "replan", "repo", "lift");
  }

  @ParameterizedTest
  @CsvSource({
    "plan, replan, false, false",
    "plan, replan, true, false",
    "plan, none, false, false",
    "plan, none, true, true",
    "none, replan, false, true",
    "none, replan, true, false",
    "none, other, false, true",
    "none, other, true, true"
  })
  void masterMayApproveFirstPlanWherePlanIsNotMineAndLaterOneWhereReplanIsNot(
      String first, String later, boolean laterPlan, boolean may) {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).planApproved(laterPlan).build();
    Arbiter.Profile profile = new Arbiter.Profile(MasterMode.ACT, Set.of(first, later));

    assertThat(profile.mayApprovePlan(task)).isEqualTo(may);
  }

  @ParameterizedTest
  @CsvSource({
    "replan, false, false",
    "replan, true, true",
    "plan, false, true",
    "plan, true, false",
    "approve-plan, true, true"
  })
  void humanKeepsApprovePlanByTheRightOfThePlanWaiting(
      String mine, boolean laterPlan, boolean keeps) {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.PLAN_PENDING).planApproved(laterPlan).build();

    assertThat(
            new Arbiter.Profile(MasterMode.ACT, Set.of(mine)).keeps(TaskAction.APPROVE_PLAN, task))
        .isEqualTo(keeps);
  }

  @ParameterizedTest
  @CsvSource({
    "ACT, none, ship, SHIP",
    "ACT, none, ' Ship ', SHIP",
    "ACT, ship, deploy, DEPLOY",
    "ACT, deploy, ship, SHIP",
    "ACT, none, deploy, DEPLOY",
    "ACT, deploy, deploy, ",
    "ACT, ship, ship, ",
    "ACT, none, lift, ",
    "ACT, none, 'keep v2', ",
    "JUDGE, none, ship, ",
    "OFF, none, deploy, "
  })
  void masterHoldsShipAndDeployAsAnAnswerOnlyInActAndWhereTheHumanKeptNeither(
      MasterMode mode, String mine, String id, TaskAction held) {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.REVIEW_PENDING).build();

    assertThat(new Arbiter.Profile(mode, Set.of(mine)).holds(id, task))
        .isEqualTo(Optional.ofNullable(held));
  }
}
