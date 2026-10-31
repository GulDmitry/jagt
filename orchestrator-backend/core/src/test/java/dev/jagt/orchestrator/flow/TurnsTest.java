package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn.Holder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TurnsTest {

    private static final Map<String, Arbiter.Profile> PROFILES = Map.of(
            "off", Arbiter.Profile.OFF,
            "judge", new Arbiter.Profile(MasterMode.JUDGE, Set.of()),
            "act-mine-plan", new Arbiter.Profile(MasterMode.ACT, Set.of(MasterRight.PLAN)),
            "act", new Arbiter.Profile(MasterMode.ACT, Set.of()));

    static Stream<Arguments> everyTurn() {
        return Arrays.stream(TaskStatus.values()).flatMap(status -> Arrays.stream(AgentReport.values())
                .flatMap(report -> PROFILES.keySet().stream().map(profile -> Arguments.of(status, report, profile))));
    }

    static Stream<Arguments> everyTurnNoMasterHolds() {
        return everyTurn().filter(row -> Turns.next((TaskStatus) row.get()[0], (AgentReport) row.get()[1], false, Map.of(),
                PROFILES.get((String) row.get()[2])) != Holder.ARBITER);
    }

    @ParameterizedTest
    @MethodSource("everyTurn")
    void namesExactlyOneHolderForEveryStatusReportAndProfile(TaskStatus status, AgentReport report, String profile) {
        Holder holder = Turns.next(status, report, false, Map.of(), PROFILES.get(profile));

        assertThat(holder).isInstanceOf(Holder.Fixed.class);
    }

    @ParameterizedTest
    @MethodSource("everyTurnNoMasterHolds")
    void theBoardShowsTheHolderWhereNoMasterHoldsTheTurn(TaskStatus status, AgentReport report, String profile) {
        Holder holder = Turns.next(status, report, false, Map.of(), PROFILES.get(profile));

        Move move = Move.forTask(status, Facts.defaults().withHasReviewRequest(true),
                RoundState.of(report, false).withMaster(PROFILES.get(profile)), false,
                AutoReviewWatch.watching(Long.MAX_VALUE));

        assertThat(move.owner()).isEqualTo(Owner.of(holder));
    }

    @ParameterizedTest
    @CsvSource({
            "IN_PROGRESS, QUESTION, act",
            "SHIPPING, QUESTION, act-mine-plan",
            "REVIEWED, PLAIN, act",
            "APPROVED, PLAIN, act"})
    void theBoardShowsTheMasterAtWorkWhereItStandsInForTheHuman(TaskStatus status, AgentReport report, String profile) {
        Move move = Move.forTask(status, Facts.defaults().withHasReviewRequest(true),
                RoundState.of(report, false).withMaster(PROFILES.get(profile)), false,
                AutoReviewWatch.watching(Long.MAX_VALUE));

        assertThat(move.owner()).isEqualTo(Owner.AGENT);
    }

    @ParameterizedTest
    @CsvSource({
            "IN_PROGRESS, QUESTION, act, HUMAN",
            "APPROVED, PLAIN, act, HUMAN",
            "REVIEW_PENDING, PLAIN, judge, HUMAN",
            "DONE, PLAIN, act, NOBODY"})
    void aTaskJagtStoppedIsTheHumansWhereverTheMasterStands(TaskStatus status, AgentReport report, String profile,
                                                           Holder.Fixed expected) {
        Holder holder = Turns.next(status, report, true, Map.of(), PROFILES.get(profile));

        assertThat(holder).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "PLAN_PENDING, PLAIN, judge",
            "PLAN_PENDING, PLAIN, act-mine-plan",
            "REVIEW_PENDING, PLAIN, act",
            "REVIEW_PENDING, NO_CHANGES, judge"})
    void aRoundTheMasterReadsIsItsTurnAndTheBoardSaysMasterReview(TaskStatus status, AgentReport report,
                                                                  String profile) {
        Holder holder = Turns.next(status, report, false, Map.of(), PROFILES.get(profile));

        Move move = Move.forTask(status, Facts.defaults().withHasReviewRequest(true),
                RoundState.of(report, false).withMasterReading(true), false);

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
            "DONE, QUESTION, act, NOBODY"})
    void theMasterHoldsTheTurnOnlyWhereItStandsInForTheHuman(TaskStatus status, AgentReport report, String profile,
                                                            Holder.Fixed expected) {
        Holder holder = Turns.next(status, report, false, Map.of(), PROFILES.get(profile));

        assertThat(holder).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"off", "judge", "act"})
    void aSessionWaitingOnADependencyKeepsItsTurnWhateverTheMasterDoes(String profile) {
        Holder holder = Turns.next(TaskStatus.IN_PROGRESS, AgentReport.WAIT, false, Map.of(), PROFILES.get(profile));

        assertThat(holder).isEqualTo(Holder.SESSION);
    }

    @ParameterizedTest
    @CsvSource({"off", "judge", "act"})
    void qaHoldsTheDeployedTurnWhileItsCheckReadsAndTheBoardShowsTheAgentAtWork(String profile) {
        Holder holder = Turns.next(TaskStatus.DEPLOYED, AgentReport.PLAIN, false, Map.of("qa", CheckerPhase.READING), PROFILES.get(profile));

        Move move = Move.forTask(TaskStatus.DEPLOYED, Facts.defaults().withHasReviewRequest(true),
                RoundState.of(AgentReport.PLAIN, false).withCheckers(Map.of("qa", CheckerPhase.READING)).withMaster(PROFILES.get(profile)), false);

        assertThat(holder).isEqualTo(new Holder.Role("qa"));
        assertThat(move.owner()).isEqualTo(Owner.AGENT);
    }

    @Test
    void nobodyHoldsADeployedTaskWhoseCheckIsNotReading() {
        Holder holder = Turns.next(TaskStatus.DEPLOYED, AgentReport.PLAIN, false, Map.of(), PROFILES.get("act"));

        assertThat(holder).isEqualTo(Holder.NOBODY);
    }

    @Test
    void aStoppedTaskIsTheHumansEvenWhereQaReads() {
        Holder holder = Turns.next(TaskStatus.DEPLOYED, AgentReport.PLAIN, true, Map.of("qa", CheckerPhase.READING), PROFILES.get("act"));

        assertThat(holder).isEqualTo(Holder.HUMAN);
    }

    @Test
    void theMasterHoldsARoundWhoseSealWasClearedForItsVerdictFileWasGone() {
        TaskState sealed = TaskReducer.masterVerdictGone(TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .master(Master.defaults().withSeal("fingerprint", 0)).build());

        Holder holder = Turns.next(sealed.status(), sealed.report(), sealed.stopped(), Checkers.of(sealed),
                new Arbiter.Profile(MasterMode.JUDGE, Set.of()).at(MasterPhase.of(sealed)));

        assertThat(holder).isEqualTo(Holder.ARBITER);
    }

    static Stream<Arguments> everyStatusReportStoppedAndQaPhase() {
        return Arrays.stream(TaskStatus.values()).flatMap(status -> Arrays.stream(AgentReport.values())
                .flatMap(report -> Stream.of(false, true).flatMap(stopped -> Arrays.stream(CheckerPhase.values())
                        .map(qa -> Arguments.of(status, report, stopped, qa)))));
    }

    @ParameterizedTest
    @MethodSource("everyStatusReportStoppedAndQaPhase")
    void theMasterHoldsNoTurnWhereItLeftTheTaskToTheHuman(TaskStatus status, AgentReport report, boolean stopped,
                                                          CheckerPhase qa) {
        Holder holder = Turns.next(status, report, stopped, Map.of("qa", qa),
                new Arbiter.Profile(MasterMode.ACT, Set.of()).at(MasterPhase.LEFT));

        assertThat(holder).isNotEqualTo(Holder.ARBITER);
    }
}
