package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinishedTaskTest {

    @Test
    void learnsFromWhereAHumanChoseToDoTheWork() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD))).build(), 2L);

        assertThat(task.routingWorthLearningFrom()).isTrue();
    }

    @Test
    void learnsFromARoutingOfItsOwnOnlyOnceTheWorkReachedTheSharedBranch() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER),
                        new StatusChange(TaskStatus.DEPLOYED, 2L, ActionOrigin.BOARD))).build(), 3L);

        assertThat(task.routingWorthLearningFrom()).isTrue();
    }

    @Test
    void learnsNothingFromARoutingOfItsOwnThatNothingEverConfirmed() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER))).build(), 2L);

        assertThat(task.routingWorthLearningFrom()).isFalse();
    }

    @Test
    void learnsNothingFromATaskThatWasDoneInSeveralRepositoriesAtOnce() {
        var task = FinishedTask.of("ABC-1", TaskState.builder(
                List.of(TaskRepo.of("sc", "/wt-sc"), TaskRepo.of("api", "/wt-api")), TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD))).build(), 2L);

        assertThat(task.routingWorthLearningFrom()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"ready,true,true,false", "' Ready ',true,true,false",
            "question,true,false,true", "needs-work,true,false,false", "'  ',false,false,false",
            "NULL,false,false,false"})
    void readsTheMastersVerdictOnlyByItsOneWordForPassingAndForAsking(String verdict, boolean judged,
                                                                    boolean passed, boolean asked) {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE).build(), 2L, verdict);

        assertThat(task.judged()).isEqualTo(judged);
        assertThat(task.passed()).isEqualTo(passed);
        assertThat(task.asked()).isEqualTo(asked);
    }

    @Test
    void saysWhoOpenedItFromTheFirstStepOnly() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD),
                        new StatusChange(TaskStatus.DONE, 2L, ActionOrigin.TRACKER))).build(), 3L);

        assertThat(task.openedBy()).isEqualTo(ActionOrigin.BOARD);
    }

    @Test
    void countsAJudgementBeforeDeployWhenTheLastReviewCameBackBeforeTheFirstDeploy() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 3L, null))).build(), 4L);

        assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isTrue();
    }

    @Test
    void countsNoJudgementBeforeDeployWhenAReviewCameBackAfterIt() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 3L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 4L, null))).build(), 5L);

        assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
    }

    @Test
    void measuresAgainstTheFirstDeployWhenTheTaskWasDeployedTwice() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 2L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 3L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 4L, null))).build(), 5L);

        assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
    }

    @Test
    void measuresAgainstTheFirstDeployEvenWhenThatWasTheVeryFirstStep() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null),
                        new StatusChange(TaskStatus.DEPLOYED, 3L, null))).build(), 4L);

        assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
    }

    @Test
    void countsNoJudgementBeforeTheReviewRoundItself() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, 2L, null))).build(), 3L);

        assertThat(task.judgedBefore(TaskStatus.REVIEW_PENDING)).isFalse();
    }

    @Test
    void countsADeployAsNeverJudgedWhenNoReviewEverCameBack() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, null))).build(), 2L);

        assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isTrue();
    }

    @Test
    void countsNoJudgementBeforeAStatusTheTaskNeverReached() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, null))).build(), 2L);

        assertThat(task.judgedBefore(TaskStatus.DEPLOYED)).isFalse();
    }

    @Test
    void takesFromItsFirstStepToTheMomentItWasRetired() {
        var task = FinishedTask.of("ABC-1", TaskState.builder("sc", "/wt", TaskStatus.DONE)
                .history(List.of(new StatusChange(TaskStatus.NEW, 1_000L, null))).build(), 6_000L);

        assertThat(task.tookMillis()).isEqualTo(5_000L);
    }

    @Test
    void tookNothingWhenNoStepWasEverRecorded() {
        var task = new FinishedTask("ABC-1", null, null, List.of(), null, 6_000L, 0, null, 0, List.of(), null, null);

        assertThat(task.tookMillis()).isZero();
    }
}
