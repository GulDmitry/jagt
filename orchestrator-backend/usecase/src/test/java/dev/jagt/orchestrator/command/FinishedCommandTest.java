package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinishedCommandTest {

    @Test
    void sumsWhatTheGuardsRefusedPerKindAcrossFinishedTasks() {
        TaskState twice = TaskState.builder("proj", "/wt", TaskStatus.DONE).build()
                .refused(Guard.SESSION_LINE).refused(Guard.SESSION_LINE);
        TaskState once = TaskState.builder("proj", "/wt", TaskStatus.DONE).build()
                .refused(Guard.SESSION_LINE).refused(Guard.STATUS_REPORT);

        String report = FinishedCommand.render(List.of(FinishedTask.of("ABC-1", twice, 1L),
                FinishedTask.of("ABC-2", once, 1L)));

        assertThat(report).endsWith("refused: session line 3, status report 1");
    }

    @Test
    void namesNoRefusalWhereNoGuardRefusedAnything() {
        String report = FinishedCommand.render(List.of(FinishedTask.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.DONE).build(), 1L)));

        assertThat(report).doesNotContain("refused");
    }
}
