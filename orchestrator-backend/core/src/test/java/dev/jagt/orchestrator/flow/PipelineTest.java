package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineTest {

    @ParameterizedTest
    @CsvSource({"success,GREEN", "failed,RED", "running,RUNNING", "none,NONE", "unknown,UNKNOWN"})
    void readsJagtsFiveWordsAsAVerdict(String said, Pipeline verdict) {
        assertThat(Pipeline.of(said)).isEqualTo(verdict);
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
    @CsvSource({"Success,GREEN", "FaIlEd,RED", "Running,RUNNING"})
    void reachesTheSameVerdictHoweverItIsCapitalised(String hostStatus, Pipeline verdict) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(verdict);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "UNREAD", value = {"UNREAD", "''", "'   '"})
    void reportsTheChecksUnreadWhenNothingHasBeenReadYet(String hostStatus) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(Pipeline.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({"wobbly", "sideways", "canceled", "in_progress"})
    void reportsTheChecksUnreadRatherThanGuessingAtAWordOutsideTheFive(String hostStatus) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(Pipeline.UNKNOWN);
    }

}
