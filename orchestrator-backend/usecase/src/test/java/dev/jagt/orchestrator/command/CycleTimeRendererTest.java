package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CycleTimeRendererTest {

    private static final long HOUR = 3_600_000L;

    private static TaskState withSteps(StatusChange... steps) {
        return TaskState.builder("proj", "/wt", steps[steps.length - 1].status())
                .history(List.of(steps)).build();
    }

    private static StatusChange step(TaskStatus status, long hoursAgo) {
        return new StatusChange(status, System.currentTimeMillis() - hoursAgo * HOUR, null);
    }

    private static String rowFor(String taskId, String report) {
        return report.lines().filter(line -> line.startsWith(taskId)).findFirst().orElseThrow();
    }

    @Test
    void chargesEachStepToWhoeverOwnedTheStatusItWasSpentIn() {
        Map<String, TaskState> tasks = Map.of("ABC-1", withSteps(
                step(TaskStatus.IN_PROGRESS, 10), step(TaskStatus.CI_POLLING, 8), step(TaskStatus.REVIEWED, 6)));

        String out = new CycleTimeRenderer().render(tasks);

        assertThat(rowFor("ABC-1", out)).containsSubsequence("10h", "6h", "2h", "2h");
    }

    @Test
    void countsOneRoundPerTripOutForReview() {
        Map<String, TaskState> tasks = Map.of("ABC-1", withSteps(
                step(TaskStatus.IN_PROGRESS, 9), step(TaskStatus.CI_POLLING, 8),
                step(TaskStatus.REVIEW_PENDING, 7), step(TaskStatus.CI_POLLING, 6)));

        String out = new CycleTimeRenderer().render(tasks);

        assertThat(rowFor("ABC-1", out)).endsWith("2");
    }

    @Test
    void marksTheFiguresAsFloorsForATaskWhoseOldestStepsHaveAgedOut() {
        StatusChange[] fifty = IntStream.range(0, 50)
                .mapToObj(step -> step(TaskStatus.CI_POLLING, 50 - step))
                .toArray(StatusChange[]::new);

        String out = new CycleTimeRenderer().render(Map.of("ABC-1", withSteps(fifty)));

        assertThat(rowFor("ABC-1", out)).contains("2d+").endsWith("50+");
        assertThat(out).contains("aged out of its history");
    }

    @Test
    void addsTheRoundsUpAcrossTasksAndGivesTheAverageInWords() {
        Map<String, TaskState> tasks = Map.of(
                "ABC-1", withSteps(step(TaskStatus.CI_POLLING, 5), step(TaskStatus.CI_POLLING, 4)),
                "ABC-2", withSteps(step(TaskStatus.CI_POLLING, 3)));

        String out = new CycleTimeRenderer().render(tasks);

        assertThat(rowFor("all tasks", out)).endsWith("3");
        assertThat(out).contains("1.5 per task");
    }

    @Test
    void namesTheSlowestStepAsAShareOfTheTimeAnyoneHeldTheTasks() {
        Map<String, TaskState> tasks = Map.of("ABC-1", withSteps(
                step(TaskStatus.IN_PROGRESS, 10), step(TaskStatus.REVIEW_PENDING, 8)));

        String out = new CycleTimeRenderer().render(tasks);

        assertThat(out).contains("you have been the slowest step: 8h of the 10h anyone has held these tasks (80%)");
    }

    @Test
    void putsTheTaskWaitingLongestOnTheHumanFirst() {
        Map<String, TaskState> tasks = Map.of(
                "ABC-1", withSteps(step(TaskStatus.REVIEW_PENDING, 2)),
                "ABC-2", withSteps(step(TaskStatus.REVIEW_PENDING, 20)));

        String out = new CycleTimeRenderer().render(tasks);

        assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
    }

    @Test
    void saysSoWhenNoTaskHasAHistoryToAddUp() {
        String out = new CycleTimeRenderer().render(Map.of());

        assertThat(out).contains("(no task has a status history yet)").doesNotContain("ROUNDS");
    }
}
