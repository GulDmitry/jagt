package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CycleTimeRendererTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_700_000_000_000L;

    @Test
    void chargesEachStepToWhoeverOwnedTheStatusItWasSpentIn() {
        Map<String, TaskState> tasks = Map.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, NOW - 10 * HOUR, null),
                        new StatusChange(TaskStatus.CI_POLLING, NOW - 8 * HOUR, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 6 * HOUR, null)))
                .build());

        String out = new CycleTimeRenderer().render(tasks, NOW);

        assertThat(out.lines().filter(line -> line.startsWith("ABC-1")).findFirst().orElseThrow())
                .containsSubsequence("10h", "6h", "2h", "2h");
    }

    @Test
    void countsOneRoundPerTripOutForReview() {
        Map<String, TaskState> tasks = Map.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, NOW - 9 * HOUR, null),
                        new StatusChange(TaskStatus.CI_POLLING, NOW - 8 * HOUR, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 7 * HOUR, null),
                        new StatusChange(TaskStatus.CI_POLLING, NOW - 6 * HOUR, null)))
                .build());

        String out = new CycleTimeRenderer().render(tasks, NOW);

        assertThat(out.lines().filter(line -> line.startsWith("ABC-1")).findFirst().orElseThrow()).endsWith("2");
    }

    @Test
    void marksTheFiguresAsFloorsForATaskWhoseOldestStepsHaveAgedOut() {
        List<StatusChange> fifty = IntStream.range(0, 50)
                .mapToObj(step -> new StatusChange(TaskStatus.CI_POLLING, NOW - (50 - step) * HOUR, null))
                .toList();

        String out = new CycleTimeRenderer().render(Map.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).history(fifty).build()), NOW);

        assertThat(out.lines().filter(line -> line.startsWith("ABC-1")).findFirst().orElseThrow())
                .contains("2d+").endsWith("50+");
        assertThat(out).contains("aged out of its history");
    }

    @Test
    void addsTheRoundsUpAcrossTasksAndGivesTheAverageInWords() {
        Map<String, TaskState> tasks = Map.of(
                "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                        .history(List.of(new StatusChange(TaskStatus.CI_POLLING, NOW - 5 * HOUR, null),
                                new StatusChange(TaskStatus.CI_POLLING, NOW - 4 * HOUR, null)))
                        .build(),
                "ABC-2", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                        .history(List.of(new StatusChange(TaskStatus.CI_POLLING, NOW - 3 * HOUR, null)))
                        .build());

        String out = new CycleTimeRenderer().render(tasks, NOW);

        assertThat(out.lines().filter(line -> line.startsWith("all tasks")).findFirst().orElseThrow()).endsWith("3");
        assertThat(out).contains("1.5 per task");
    }

    @Test
    void namesTheSlowestStepAsAShareOfTheTimeAnyoneHeldTheTasks() {
        Map<String, TaskState> tasks = Map.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, NOW - 10 * HOUR, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 8 * HOUR, null)))
                .build());

        String out = new CycleTimeRenderer().render(tasks, NOW);

        assertThat(out).contains("you have been the slowest step: 8h of the 10h anyone has held these tasks (80%)");
    }

    @Test
    void putsTheTaskWaitingLongestOnTheHumanFirst() {
        Map<String, TaskState> tasks = Map.of(
                "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                        .history(List.of(new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 2 * HOUR, null)))
                        .build(),
                "ABC-2", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                        .history(List.of(new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 20 * HOUR, null)))
                        .build());

        String out = new CycleTimeRenderer().render(tasks, NOW);

        assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
    }

    @Test
    void saysSoWhenNoTaskHasAHistoryToAddUp() {
        String out = new CycleTimeRenderer().render(Map.of(), NOW);

        assertThat(out).contains("(no task has a status history yet)").doesNotContain("ROUNDS");
    }
}
