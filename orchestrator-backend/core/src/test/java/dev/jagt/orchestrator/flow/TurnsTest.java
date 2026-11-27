package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.flow.view.Owner;
import dev.jagt.orchestrator.flow.view.RoundState;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn.Holder;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class TurnsTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});

  private static final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  private static final Map<String, Arbiter.Profile> PROFILES =
      Map.of(
          "off", Arbiter.Profile.OFF,
          "judge", new Arbiter.Profile(MasterMode.JUDGE, Set.of()),
          "act-mine-plan", new Arbiter.Profile(MasterMode.ACT, Set.of("plan")),
          "act", new Arbiter.Profile(MasterMode.ACT, Set.of()));

  private static final Facts withReviewRequest =
      Facts.defaults()
          .withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true).withChecksGreen(true));

  private static final AutoReviewWatch neverExpiringWatch =
      AutoReviewWatch.watching(Long.MAX_VALUE);

  static Stream<Arguments> everyTurn() {
    return Arrays.stream(TaskStatus.values())
        .flatMap(status ->
            Arrays.stream(AgentReport.values())
                .flatMap(report ->
                    PROFILES.keySet().stream()
                        .map(profile -> Arguments.of(status, report, profile))));
  }

  static Stream<Arguments> everyTurnNoMasterHolds() {
    return everyTurn()
        .filter(row ->
            !Objects.equals(
                Turns.next(
                    Objects.requireNonNull((TaskStatus) row.get()[0]),
                    Objects.requireNonNull((AgentReport) row.get()[1]),
                    StopHold.NONE,
                    Map.of(),
                    Objects.requireNonNull(PROFILES.get((String) row.get()[2])),
                    Facts.defaults()),
                Holder.ARBITER));
  }

  @ParameterizedTest
  @MethodSource("everyTurn")
  void namesExactlyOneHolderForEveryStatusReportAndProfile(
      TaskStatus status, AgentReport report, String profile) {
    Holder holder =
        Turns.next(
            status,
            report,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get(profile)),
            Facts.defaults());

    assertThat(holder).isInstanceOf(Holder.Fixed.class);
  }

  @ParameterizedTest
  @MethodSource("everyTurnNoMasterHolds")
  void theBoardShowsTheHolderWhereNoMasterHoldsTheTurn(
      TaskStatus status, AgentReport report, String profile) {
    Holder holder =
        Turns.next(
            status,
            report,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get(profile)),
            Facts.defaults());

    Move move =
        Move.forTask(
            rules,
            status,
            withReviewRequest,
            RoundState.of(report, false).withArbiter(Objects.requireNonNull(PROFILES.get(profile))),
            false,
            neverExpiringWatch);

    assertThat(move.owner()).isEqualTo(Owner.of(holder));
  }

  @ParameterizedTest
  @CsvSource({
    "IN_PROGRESS, QUESTION, act",
    "SHIPPING, QUESTION, act-mine-plan",
    "REVIEWED, PLAIN, act",
    "APPROVED, PLAIN, act"
  })
  void theBoardShowsTheMasterAtWorkWhereItStandsInForTheHuman(
      TaskStatus status, AgentReport report, String profile) {
    Move move =
        Move.forTask(
            rules,
            status,
            withReviewRequest,
            RoundState.of(report, false).withArbiter(Objects.requireNonNull(PROFILES.get(profile))),
            false,
            neverExpiringWatch);

    assertThat(move.owner()).isEqualTo(Owner.AGENT);
  }

  @ParameterizedTest
  @CsvSource({
    "IN_PROGRESS, QUESTION, act, HUMAN",
    "APPROVED, PLAIN, act, HUMAN",
    "REVIEW_PENDING, PLAIN, judge, HUMAN",
    "DONE, PLAIN, act, NOBODY"
  })
  void taskJagtStoppedIsTheHumansWhereverTheMasterStands(
      TaskStatus status, AgentReport report, String profile, Holder.Fixed expected) {
    Holder holder =
        Turns.next(
            status,
            report,
            StopHold.HUMAN,
            Map.of(),
            Objects.requireNonNull(PROFILES.get(profile)),
            Facts.defaults());

    assertThat(holder).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "PLAN_PENDING, PLAIN, judge",
    "PLAN_PENDING, PLAIN, act-mine-plan",
    "REVIEW_PENDING, PLAIN, act",
    "REVIEW_PENDING, NO_CHANGES, judge"
  })
  void roundTheMasterReadsIsItsTurnAndTheBoardSaysMasterReview(
      TaskStatus status, AgentReport report, String profile) {
    Holder holder =
        Turns.next(
            status,
            report,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get(profile)),
            Facts.defaults());

    Move move =
        Move.forTask(
            rules,
            status,
            withReviewRequest,
            RoundState.of(report, false).withArbiterReading(true),
            false);

    assertThat(holder).isEqualTo(Holder.ARBITER);
    assertThat(move.owner()).isEqualTo(Owner.AGENT);
  }

  @ParameterizedTest
  @CsvSource({
    "IN_PROGRESS, QUESTION, act, ARBITER",
    "IN_PROGRESS, QUESTION, act-mine-plan, ARBITER",
    "IN_PROGRESS, QUESTION, judge, HUMAN",
    "IN_PROGRESS, PERMISSION, act, HUMAN",
    "PLAN_PENDING, QUESTION, judge, ARBITER",
    "REVIEWED, PLAIN, act, ARBITER",
    "APPROVED, PLAIN, act-mine-plan, ARBITER",
    "APPROVED, PLAIN, judge, HUMAN",
    "DONE, QUESTION, act, NOBODY"
  })
  void theMasterHoldsTheTurnOnlyWhereItStandsInForTheHuman(
      TaskStatus status, AgentReport report, String profile, Holder.Fixed expected) {
    Holder holder =
        Turns.next(
            status,
            report,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get(profile)),
            Facts.defaults());

    assertThat(holder).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"REVIEWED, CI", "APPROVED, ARBITER"})
  void theMasterHoldsNoTurnOnReadRequestWhereTheHostsApprovalIsRequired(
      TaskStatus status, Holder.Fixed expected) {
    Holder holder =
        Turns.next(
            status,
            AgentReport.PLAIN,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get("act")),
            Facts.defaults().withReach(Facts.Reach.defaults().withRequireApproval(true)));

    assertThat(holder).isEqualTo(expected);
  }

  @Test
  void theBoardShowsReadRequestWaitingOnTheHostWhereItsApprovalIsRequired() {
    Move move =
        Move.forTask(
            rules,
            TaskStatus.REVIEWED,
            withReviewRequest.withReach(Facts.Reach.defaults().withRequireApproval(true)),
            RoundState.of(AgentReport.PLAIN, false)
                .withArbiter(Objects.requireNonNull(PROFILES.get("act"))),
            false,
            neverExpiringWatch);

    assertThat(move.owner()).isEqualTo(Owner.CI);
  }

  @ParameterizedTest
  @CsvSource({"off", "judge", "act"})
  void sessionWaitingOnDependencyKeepsItsTurnWhateverTheMasterDoes(String profile) {
    Holder holder =
        Turns.next(
            TaskStatus.IN_PROGRESS,
            AgentReport.WAIT,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get(profile)),
            Facts.defaults());

    assertThat(holder).isEqualTo(Holder.SESSION);
  }

  @ParameterizedTest
  @CsvSource({"off", "judge", "act"})
  void acceptanceHoldsTheDeployedTurnWhileItsCheckReadsAndTheBoardShowsTheAgentAtWork(
      String profile) {
    Holder holder =
        Turns.next(
            TaskStatus.DEPLOYED,
            AgentReport.PLAIN,
            StopHold.NONE,
            Map.of("acceptance", CheckerPhase.READING),
            Objects.requireNonNull(PROFILES.get(profile)),
            Facts.defaults());

    Move move =
        Move.forTask(
            rules,
            TaskStatus.DEPLOYED,
            withReviewRequest,
            RoundState.of(AgentReport.PLAIN, false)
                .withCheckers(Map.of("acceptance", CheckerPhase.READING))
                .withArbiter(Objects.requireNonNull(PROFILES.get(profile))),
            false);

    assertThat(holder).isEqualTo(new Holder.Role("acceptance"));
    assertThat(move.owner()).isEqualTo(Owner.AGENT);
  }

  @ParameterizedTest
  @CsvSource({"REVIEWED", "APPROVED"})
  void theHostHoldsRequestWaitingForDeployWhileItsChecksAreNotGreen(TaskStatus status) {
    Holder holder =
        Turns.next(
            status,
            AgentReport.PLAIN,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get("act")),
            Facts.defaults().withDelivery(Facts.Delivery.defaults().withHasReviewRequest(true)));

    assertThat(holder).isEqualTo(Holder.CI);
  }

  @Test
  void nobodyHoldsDeployedTaskWhoseCheckIsNotReading() {
    Holder holder =
        Turns.next(
            TaskStatus.DEPLOYED,
            AgentReport.PLAIN,
            StopHold.NONE,
            Map.of(),
            Objects.requireNonNull(PROFILES.get("act")),
            Facts.defaults());

    assertThat(holder).isEqualTo(Holder.NOBODY);
  }

  @Test
  void stoppedTaskIsTheHumansEvenWhereAcceptanceReads() {
    Holder holder =
        Turns.next(
            TaskStatus.DEPLOYED,
            AgentReport.PLAIN,
            StopHold.HUMAN,
            Map.of("acceptance", CheckerPhase.READING),
            Objects.requireNonNull(PROFILES.get("act")),
            Facts.defaults());

    assertThat(holder).isEqualTo(Holder.HUMAN);
  }

  @Test
  void theMasterHoldsRoundWhoseSealWasClearedForItsVerdictFileWasGone() {
    TaskState sealed =
        reducer.arbiterVerdictGone(
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .master(Master.defaults().withSeal("fingerprint", 0))
                .build());

    Holder holder =
        Turns.next(
            sealed.standing().status(),
            sealed.standing().report(),
            sealed.work().stopped() ? StopHold.HUMAN : StopHold.NONE,
            new Checkers(new TaskChart(Composition.builtIn())).of(sealed),
            new Arbiter.Profile(MasterMode.JUDGE, Set.of()).at(MasterPhase.of(sealed)),
            Facts.defaults());

    assertThat(holder).isEqualTo(Holder.ARBITER);
  }

  static Stream<Arguments> everyStatusReportStoppedAndAcceptancePhase() {
    return Arrays.stream(TaskStatus.values())
        .flatMap(status ->
            Arrays.stream(AgentReport.values())
                .flatMap(report ->
                    Stream.of(false, true)
                        .flatMap(stopped ->
                            Arrays.stream(CheckerPhase.values())
                                .map(acceptance ->
                                    Arguments.of(status, report, stopped, acceptance)))));
  }

  @ParameterizedTest
  @MethodSource("everyStatusReportStoppedAndAcceptancePhase")
  void theMasterHoldsNoTurnWhereItLeftTheTaskToTheHuman(
      TaskStatus status, AgentReport report, boolean stopped, CheckerPhase acceptance) {
    Holder holder =
        Turns.next(
            status,
            report,
            stopped ? StopHold.HUMAN : StopHold.NONE,
            Map.of("acceptance", acceptance),
            new Arbiter.Profile(MasterMode.ACT, Set.of()).at(MasterPhase.LEFT),
            Facts.defaults());

    assertThat(holder).isNotEqualTo(Holder.ARBITER);
  }
}
