package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ReviewDrafts;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipDueTest {

    private final WorktreeChanges changes = mock(WorktreeChanges.class);
    private final ReviewDrafts drafts = mock(ReviewDrafts.class);
    private final TaskStops stops = mock(TaskStops.class);

    @Test
    void isNotDueForAHandBackWhoseBranchIsPushedUnchangedAndDraftsNothing() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(stops.mayShip("ABC-42")).thenReturn(true);

        assertThat(new ShipDue(changes, drafts, stops).due("ABC-42", task)).isFalse();
    }

    @Test
    void isDueForAHandBackCarryingNewWork() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.anythingNewToShip(task)).thenReturn(true);
        when(stops.mayShip("ABC-42")).thenReturn(true);

        assertThat(new ShipDue(changes, drafts, stops).due("ABC-42", task)).isTrue();
    }

    @Test
    void isDueForAHandBackWhoseBranchIsUnchangedButDraftsReplies() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(drafts.drafted(task)).thenReturn(true);
        when(stops.mayShip("ABC-42")).thenReturn(true);

        assertThat(new ShipDue(changes, drafts, stops).due("ABC-42", task)).isTrue();
    }

    @Test
    void isNotDueWhereTheLoopStopForbidsAnotherShip() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.anythingNewToShip(task)).thenReturn(true);

        assertThat(new ShipDue(changes, drafts, stops).due("ABC-42", task)).isFalse();
    }
}
