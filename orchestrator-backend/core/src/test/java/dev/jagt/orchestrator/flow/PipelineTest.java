package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineTest {

    @ParameterizedTest
    @CsvSource({"success,GREEN", "failed,RED", "canceled,RED", "running,RUNNING", "pending,RUNNING"})
    void readsGitLabsOwnWordingAsAVerdict(String hostStatus, Pipeline verdict) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(verdict);
    }

    @ParameterizedTest
    @CsvSource({"SUCCESS,GREEN", "FAILURE,RED", "ERROR,RED", "TIMED_OUT,RED", "ACTION_REQUIRED,RED",
            "IN_PROGRESS,RUNNING", "QUEUED,RUNNING"})
    void readsGitHubsOwnWordingAsAVerdict(String hostStatus, Pipeline verdict) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(verdict);
    }

    @Test
    void refusesToReadAFinishedRunAsAPassedOne() {
        assertThat(Pipeline.of("COMPLETED")).isEqualTo(Pipeline.UNKNOWN);
    }

    @Test
    void ranksARedRunAsWorseNewsThanNoAnswerYetAndThatWorseThanGreen() {
        assertThat(Pipeline.RED.severity()).isLessThan(Pipeline.RUNNING.severity());
        assertThat(Pipeline.RUNNING.severity()).isLessThan(Pipeline.UNKNOWN.severity());
        assertThat(Pipeline.UNKNOWN.severity()).isLessThan(Pipeline.NONE.severity());
        assertThat(Pipeline.NONE.severity()).isLessThan(Pipeline.GREEN.severity());
    }

    @ParameterizedTest
    @CsvSource({"Success,GREEN", "FaIlEd,RED", "Running,RUNNING", "Canceled,RED", "In_Progress,RUNNING"})
    void reachesTheSameVerdictHoweverTheHostCapitalisesIt(String hostStatus, Pipeline verdict) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(verdict);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "UNREAD", value = {"UNREAD", "''", "'   '"})
    void reportsTheChecksUnreadWhenNothingHasBeenReadYet(String hostStatus) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(Pipeline.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({"wobbly", "sideways", "moonwalk"})
    void reportsTheChecksUnreadRatherThanGuessingAtAWordNoHostUses(String hostStatus) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(Pipeline.UNKNOWN);
    }

    @Test
    void reportsNoChecksOnlyWhereTheHostItselfListedNoPipeline() {
        assertThat(Pipeline.of("none")).isEqualTo(Pipeline.NONE);
    }

    @Test
    void interruptsAHumanForARunThatWentRed() {
        assertThat(Pipeline.RED.worthATap()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Pipeline.class, names = "RED", mode = EnumSource.Mode.EXCLUDE)
    void interruptsNobodyForAnyOtherVerdict(Pipeline verdict) {
        assertThat(verdict.worthATap()).isFalse();
    }

}
