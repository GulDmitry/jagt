package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterRecordTest {

    private final FinishedTasks finished = mock(FinishedTasks.class);
    private final MasterRecord record = new MasterRecord(finished);

    @Test
    void countsNothingWhereTheReviewerHasJudgedNothing() {
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.DONE).build(), 1L)));

        assertThat(record.seen().any()).isFalse();
    }

    @Test
    void readsWorkItPassedThatNobodyEverDeployedAsADisagreement() {
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD))).build(),
                2L, "ready")));

        assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 1, 0, 1, 0, 0));
    }

    @Test
    void readsWorkItFailedThatYouDeployedAnywayAsADisagreement() {
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD)))
                        .build(), 2L, "not ready")));

        assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 0, 0, 0, 0, 1));
    }

    @Test
    void readsWorkItPassedAndYouDeployedAsAgreement() {
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD)))
                        .build(), 2L, "ready")));

        assertThat(record.seen().disagreed()).isZero();
    }

    @Test
    void readsWorkItPassedThatYouRevertedAsADisagreement() {
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD),
                                new StatusChange(TaskStatus.REVERTED, 2L, ActionOrigin.BOARD)))
                        .build(), 3L, "ready")));

        assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 1, 0, 0, 1, 0));
    }

    @Test
    void readsAQuestionYouAnsweredAndDeployedAsNoDisagreement() {
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD)))
                        .build(), 2L, "question")));

        assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 0, 1, 0, 0, 0));
    }
}
