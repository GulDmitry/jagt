package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

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
}
