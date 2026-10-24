package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn.Holder;
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

    private static final Map<String, Turns.Profile> PROFILES = Map.of(
            "off", Turns.Profile.OFF,
            "judge", new Turns.Profile(MasterMode.JUDGE, Set.of()),
            "act-mine-plan", new Turns.Profile(MasterMode.ACT, Set.of(MasterRight.PLAN)),
            "act", new Turns.Profile(MasterMode.ACT, Set.of()));

    static Stream<Arguments> everyTurn() {
        return Arrays.stream(TaskStatus.values()).flatMap(status -> Arrays.stream(AgentReport.values())
                .flatMap(report -> PROFILES.keySet().stream().map(profile -> Arguments.of(status, report, profile))));
    }

    static Stream<Arguments> everyTurnNoMasterHolds() {
        return everyTurn().filter(row -> Turns.next((TaskStatus) row.get()[0], (AgentReport) row.get()[1], false,
                PROFILES.get((String) row.get()[2])) != Holder.MASTER);
    }

    @ParameterizedTest
    @MethodSource("everyTurn")
    void namesExactlyOneHolderForEveryStatusReportAndProfile(TaskStatus status, AgentReport report, String profile) {
        Holder holder = Turns.next(status, report, false, PROFILES.get(profile));

        assertThat(holder).isIn((Object[]) Holder.values());
    }

    @ParameterizedTest
    @MethodSource("everyTurnNoMasterHolds")
    void theBoardShowsTheHolderWhereNoMasterHoldsTheTurn(TaskStatus status, AgentReport report, String profile) {
        Holder holder = Turns.next(status, report, false, PROFILES.get(profile));

        Move move = Move.forTask(status, new Facts(true, false, () -> false, false, false, false),
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
        Move move = Move.forTask(status, new Facts(true, false, () -> false, false, false, false),
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
                                                           Holder expected) {
        Holder holder = Turns.next(status, report, true, PROFILES.get(profile));

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
        Holder holder = Turns.next(status, report, false, PROFILES.get(profile));

        Move move = Move.forTask(status, new Facts(true, false, () -> false, false, false, false),
                RoundState.of(report, false).withMasterReading(true), false);

        assertThat(holder).isEqualTo(Holder.MASTER);
        assertThat(move.owner()).isEqualTo(Owner.AGENT);
    }

    @ParameterizedTest
    @CsvSource({
            "IN_PROGRESS, QUESTION, act, MASTER",
            "IN_PROGRESS, QUESTION, act-mine-plan, MASTER",
            "IN_PROGRESS, QUESTION, judge, HUMAN",
            "IN_PROGRESS, PERMISSION, act, HUMAN",
            "PLAN_PENDING, QUESTION, judge, MASTER",
            "REVIEWED, PLAIN, act, MASTER",
            "APPROVED, PLAIN, act-mine-plan, MASTER",
            "APPROVED, PLAIN, judge, HUMAN",
            "DONE, QUESTION, act, NOBODY"})
    void theMasterHoldsTheTurnOnlyWhereItStandsInForTheHuman(TaskStatus status, AgentReport report, String profile,
                                                            Holder expected) {
        Holder holder = Turns.next(status, report, false, PROFILES.get(profile));

        assertThat(holder).isEqualTo(expected);
    }
}
