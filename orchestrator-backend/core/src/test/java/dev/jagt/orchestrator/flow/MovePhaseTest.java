package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Attention;
import dev.jagt.orchestrator.flow.view.Owner;
import dev.jagt.orchestrator.flow.view.Phase;
import dev.jagt.orchestrator.flow.view.RoundState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.turn.AgentReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MovePhaseTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"APPROVED", "REVERTED"})
  void offersTheNextMoveWithoutInterruptingWhenNothingIsStuck(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.OPTIONAL);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_FAILED", "DEPLOY_CONFLICT"})
  void interruptsForTaskThatMovesNoFurtherWithoutTheHuman(TaskStatus status) {
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false)
                .attention())
        .isEqualTo(Attention.REQUIRED);
  }

  @Test
  void interruptsWhenAnAgentAsksFromStatusThatWouldOtherwiseWait() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.APPROVED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
  }

  @Test
  void interruptsForAnAgentThatWentQuietWithoutReportingAnything() {
    Move move =
        Move.forTask(rules, TaskStatus.IN_PROGRESS, Facts.defaults(), RoundState.NONE, true);

    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
  }

  @Test
  void waitsOnTheReviewerAfterCleanRoundThatNobodyHasApprovedYet() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.watching(60_000));

    assertThat(move.owner()).isEqualTo(Owner.CI);
    assertThat(move.primary()).isNull();
    assertThat(move.actions()).contains(TaskAction.DEPLOY);
    assertThat(move.hint()).contains("waiting for an approval");
  }

  @Test
  void highlightsTheReadWhenNothingIsPollingForTheApprovalAtAll() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.CI);
    assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
    assertThat(move.hint()).contains("nothing is polling for the approval");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEW_PENDING", "CI_FAILED", "REVIEWED", "APPROVED", "REVERTED", "DEPLOYED"})
  void highlightsTheAnswerRatherThanVerbThatActsWhileQuestionIsOpen(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
  }

  @Test
  void handsCleanRoundBackToTheHumanOnceNothingPollsItForTheApproval() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false,
            AutoReviewWatch.windowElapsed(24));

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.primary()).isEqualTo(TaskAction.SWEEP);
    assertThat(move.hint()).contains("nothing is polling for the approval");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"CI_POLLING", "REVIEWED"})
  void handsTheTaskOverForQuestionAskedFromStatusThatWaitsOnTheCodeHost(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.hint()).contains("answer the question");
  }

  @Test
  void leavesClosedTaskAloneWhateverItsLastMessageSaid() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.DONE,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.NOBODY);
  }

  @ParameterizedTest
  @CsvSource({
    "REVIEW_PENDING,false,REVIEW",
    "CI_POLLING,true,CHECK",
    "REVIEWED,true,READY",
    "APPROVED,true,READY"
  })
  void collapsesTheFourStatusesThatAllReadAsReviewIntoDistinctPhases(
      TaskStatus status, boolean hasRequest, Phase phase) {
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(hasRequest)),
                    RoundState.NONE,
                    false)
                .phase())
        .isEqualTo(phase);
  }

  @Test
  void neverMakesDeployThePrimaryMoveOfTaskThatHasNoRequestToLand() {
    Move move = Move.forTask(rules, TaskStatus.REVIEWED, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
    assertThat(move.actions()).contains(move.primary());
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NOTHING",
      value = {
        "REVIEW_PENDING,false,SHIP",
        "CI_FAILED,true,SWEEP",
        "APPROVED,true,DEPLOY",
        "DEPLOYED,true,DONE",
        "IN_PROGRESS,false,FOCUS",
        "DONE,false,NOTHING"
      })
  void pointsAtTheOneObviousActionForEachStatus(
      TaskStatus status, boolean hasRequest, TaskAction primary) {
    assertThat(
            Move.forTask(
                    rules,
                    status,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(hasRequest)),
                    RoundState.NONE,
                    false)
                .primary())
        .isEqualTo(primary);
  }

  @Test
  void offersShipForTaskStuckAtShippingBecauseTheDeadAgentIsWhatMakesItStuck() {
    assertThat(
            Move.forTask(rules, TaskStatus.SHIPPING, Facts.defaults(), RoundState.NONE, false)
                .actions())
        .contains(TaskAction.SHIP);
  }

  @Test
  void advisesFixOntoTheSameRequestForTaskWhoseDeployWasTakenBackOut() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVERTED,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.SHIP);
    assertThat(move.actions()).contains(TaskAction.SHIP);
  }

  @Test
  void sendsTheHumanToTheAgentWhenReversionHasNoRequestToShipOnto() {
    assertThat(
            Move.forTask(rules, TaskStatus.REVERTED, Facts.defaults(), RoundState.NONE, false)
                .primary())
        .isEqualTo(TaskAction.FOCUS);
  }

  @Test
  void leavesReversionInTheDeployPhaseBecauseThatIsWhereItWentWrong() {
    assertThat(
            Move.forTask(
                    rules,
                    TaskStatus.REVERTED,
                    Facts.defaults()
                        .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
                    RoundState.NONE,
                    false)
                .phase())
        .isEqualTo(Phase.DEPLOY);
  }

  @Test
  void asksTheHumanToCheckTheStoppedSessionWhereTheAgentWentSilent() {
    Move move =
        Move.forTask(rules, TaskStatus.IN_PROGRESS, Facts.defaults(), RoundState.NONE, true);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.ask()).isEqualTo("check the stopped session");
  }

  @Test
  void asksTheHumanToAnswerTheSessionWhereItStoppedOnQuestion() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults(),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.ask()).isEqualTo("answer the session");
  }

  @Test
  void asksTheHumanToReviewTheRoundWhereTheQuestionCameBackWithTheHandBack() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.ask()).isEqualTo("review the round");
  }

  @Test
  void asksNothingOfHumanWhileTheAgentIsWorking() {
    Move move =
        Move.forTask(rules, TaskStatus.IN_PROGRESS, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.ask()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "REVIEW_PENDING", "CI_FAILED"})
  void putsAddRepoOnTheQuestionOfSessionAskingForRepository(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults().withWorkspace(Facts.Workspace.defaults().withRepoRequested(true)),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.REQUIRED);
    assertThat(move.primary()).isEqualTo(TaskAction.ADD_REPO);
    assertThat(move.actions()).contains(TaskAction.ADD_REPO, TaskAction.FOCUS);
  }

  @Test
  void keepsFocusOnTheQuestionOfSessionAskingForNothingTheBoardCanAdd() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults(),
            RoundState.of(AgentReport.QUESTION, false),
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
    assertThat(move.actions()).doesNotContain(TaskAction.ADD_REPO);
  }

  @Test
  void offersNoAddRepoOnceTheSessionIsWorkingAgainWhateverRequestIsLeftOnTheTask() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.IN_PROGRESS,
            Facts.defaults().withWorkspace(Facts.Workspace.defaults().withRepoRequested(true)),
            RoundState.NONE,
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.FOCUS);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEWED", "APPROVED"})
  void offersTheCloseQuietlyOnceEveryRequestIsMerged(TaskStatus status) {
    Move move =
        Move.forTask(
            rules,
            status,
            Facts.defaults()
                .withDelivery(
                    Facts.Delivery.defaults().withHasReviewRequest(true).withRequestMerged(true)),
            RoundState.NONE,
            false);

    assertThat(move.owner()).isEqualTo(Owner.YOU);
    assertThat(move.attention()).isEqualTo(Attention.OPTIONAL);
    assertThat(move.ask()).isEqualTo("you can close the task");
    assertThat(move.primary()).isEqualTo(TaskAction.DONE);
  }

  @Test
  void advisesTheDeployOfTaskShippedWithoutRequestSinceNoReviewIsOpenOnIt() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEW_PENDING,
            Facts.defaults()
                .withDelivery(Facts.Delivery.defaults().withShippedWithoutRequest(true)),
            RoundState.NONE,
            false);

    assertThat(move.primary()).isEqualTo(TaskAction.DEPLOY);
    assertThat(move.actions()).contains(TaskAction.DEPLOY);
  }

  @Test
  void offersNoDeployToTaskAtReviewPendingThatWasNeitherShippedNorRequested() {
    Move move =
        Move.forTask(rules, TaskStatus.REVIEW_PENDING, Facts.defaults(), RoundState.NONE, false);

    assertThat(move.actions()).doesNotContain(TaskAction.DEPLOY);
  }
}
