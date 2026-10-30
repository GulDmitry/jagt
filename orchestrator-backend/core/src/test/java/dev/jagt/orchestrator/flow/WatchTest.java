package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WatchTest {

    @Test
    void admitsAnyUnstoppedTaskWhereNoRegionIsNamed() {
        TaskState unstopped = TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED).build();

        assertThat(Watch.defaults().admits(unstopped)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"NEW,true", "IN_PROGRESS,false", "DONE,false"})
    void admitsATaskOnlyInANamedStatus(TaskStatus status, boolean admitted) {
        TaskState task = TaskState.builder("proj", "/wt", status).build();

        assertThat(Watch.defaults().withStatuses(TaskStatus.NEW).admits(task)).isEqualTo(admitted);
    }

    @ParameterizedTest
    @CsvSource({"UNDELIVERED,true", "CHECKS,false"})
    void admitsAStoppedTaskOnlyWhereItsStopEndsAsNamed(TaskStop.Trigger trigger, boolean admitted) {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .stop(new TaskStop(trigger, 1, "evidence")).build();

        assertThat(Watch.defaults().withStops(StopKind.SESSION_ALIVE).admits(task)).isEqualTo(admitted);
    }

    @Test
    void refusesAStoppedTaskWhereNoStopIsNamed() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .stop(new TaskStop(TaskStop.Trigger.PACE, 1, "evidence")).build();

        assertThat(Watch.defaults().admits(task)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PLAIN,true", "QUESTION,false"})
    void leavesOutAReportThatAsksWhenTheWatchExcludesIt(AgentReport report, boolean admitted) {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).report(report).build();

        assertThat(Watch.defaults().withReports(said -> !said.asks()).admits(task)).isEqualTo(admitted);
    }
}
