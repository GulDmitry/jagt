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

    /**
     * On some hosts `completed` says only that a run FINISHED, with the verdict reported separately — reading it
     * as green is what would advance a task on a failed build.
     */
    @Test
    void refusesToReadAFinishedRunAsAPassedOne() {
        assertThat(Pipeline.of("COMPLETED")).isEqualTo(Pipeline.NONE);
    }

    /** Merging several repositories' rounds shows the worst, so the order has to be the one a human would pick. */
    @Test
    void ranksARedRunAsWorseNewsThanNoAnswerYetAndThatWorseThanGreen() {
        assertThat(Pipeline.RED.severity()).isLessThan(Pipeline.RUNNING.severity());
        assertThat(Pipeline.RUNNING.severity()).isLessThan(Pipeline.NONE.severity());
        assertThat(Pipeline.NONE.severity()).isLessThan(Pipeline.GREEN.severity());
    }

    @ParameterizedTest
    @CsvSource({"Success,GREEN", "FaIlEd,RED", "Running,RUNNING", "Canceled,RED", "In_Progress,RUNNING"})
    void reachesTheSameVerdictHoweverTheHostCapitalisesIt(String hostStatus, Pipeline verdict) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(verdict);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "UNREAD", value = {"UNREAD", "''", "'   '"})
    void reportsNoChecksWhenNothingHasBeenReadYet(String hostStatus) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(Pipeline.NONE);
    }

    @ParameterizedTest
    @CsvSource({"wobbly", "sideways", "moonwalk"})
    void reportsNoChecksRatherThanGuessingAtAWordNoHostUses(String hostStatus) {
        assertThat(Pipeline.of(hostStatus)).isEqualTo(Pipeline.NONE);
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
