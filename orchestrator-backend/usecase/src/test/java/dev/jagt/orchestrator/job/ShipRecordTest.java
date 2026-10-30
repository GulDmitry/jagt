package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShipRecordTest {

    private final TaskTurns turns = mock(TaskTurns.class);
    private final WorktreeChanges changes = mock(WorktreeChanges.class);

    @Test
    void aPressRecordsTheTreeItShipped() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("tree-1;"));

        new ShipRecord(turns, changes).pressedOn("ABC-1", task);

        verify(turns).mark("ABC-1", TaskTurns.PRESSED_SHIP, "tree-1;");
    }

    @Test
    void aTreeGitCouldNotReadLeavesNoMark() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.empty());

        new ShipRecord(turns, changes).pressedOn("ABC-1", task);

        verifyNoInteractions(turns);
    }
}
