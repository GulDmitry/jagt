package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.FinishedTasks;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinishedCommandTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void givesTheMedianTimeAFinishedTaskTook() {
        TaskState oneHour = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null))).build();
        TaskState twoHours = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null))).build();
        TaskState nineHours = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null))).build();

        String report = FinishedCommand.render(List.of(FinishedTask.of("ABC-1", oneHour, HOUR),
                FinishedTask.of("ABC-2", twoHours, 2 * HOUR), FinishedTask.of("ABC-3", nineHours, 9 * HOUR)));

        assertThat(report).contains("median 2h");
    }

    @Test
    void splitsTheHeldTimeOfFinishedTasksBetweenYouTheAgentsAndTheCodeHost() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null),
                        new StatusChange(TaskStatus.CI_POLLING, 2 * HOUR, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 3 * HOUR, null),
                        new StatusChange(TaskStatus.DONE, 10 * HOUR, null)))
                .build();

        String report = FinishedCommand.render(List.of(FinishedTask.of("ABC-1", task, 12 * HOUR)));

        assertThat(report).contains("held by you 7h (70%), the agents 2h (20%), the code host 1h (10%)");
    }

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

    @Test
    void listsEachFinishedTaskWithHowLongItTookAndHowManyRoundsItCost() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DONE).title("Widget")
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null),
                        new StatusChange(TaskStatus.CI_POLLING, HOUR, null),
                        new StatusChange(TaskStatus.DONE, 2 * HOUR, null)))
                .build();

        String report = FinishedCommand.render(List.of(FinishedTask.of("ABC-1", task, 3 * HOUR)));

        assertThat(report).contains("  ABC-1          3h        1 round  Widget");
    }

    @Test
    void writesAMinusForATaskWhoseDurationIsUnknownAndCountsItInNoMedian() {
        TaskState unknown = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null))).build();
        TaskState twoHours = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null))).build();

        String report = FinishedCommand.render(List.of(FinishedTask.of("ABC-1", unknown, 0),
                FinishedTask.of("ABC-2", twoHours, 2 * HOUR)));

        assertThat(report).contains("  ABC-1          -         0 rounds").contains("median 2h");
    }

    @Test
    void namesOnlyTheHoldersThatActuallyHeldTheTask() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 0, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2 * HOUR, null),
                        new StatusChange(TaskStatus.DONE, 3 * HOUR, null)))
                .build();

        String report = FinishedCommand.render(List.of(FinishedTask.of("ABC-1", task, 3 * HOUR)));

        assertThat(report).contains("held by you 1h (33%), the agents 2h (67%)").doesNotContain("code host");
    }

    @Test
    void saysNothingIsFinishedWhereNoTaskWasRetired() {
        FinishedTasks finished = mock(FinishedTasks.class);
        when(finished.all()).thenReturn(List.of());
        when(finished.archives()).thenReturn(List.of());

        String report = new FinishedCommand(finished).run("");

        assertThat(report).isEqualTo("nothing finished yet — a task is recorded here when `done` retires it.");
    }

    @Test
    void namesTheArchivesHoldingOlderTasks() {
        FinishedTasks finished = mock(FinishedTasks.class);
        when(finished.all()).thenReturn(List.of());
        when(finished.archives()).thenReturn(List.of(Path.of("/data/finished-2026-01.jsonl.gz"),
                Path.of("/data/finished-2026-02.jsonl.gz")));

        String report = new FinishedCommand(finished).run("");

        assertThat(report).endsWith("\nolder tasks are packed in finished-2026-01.jsonl.gz, finished-2026-02.jsonl.gz");
    }
}
