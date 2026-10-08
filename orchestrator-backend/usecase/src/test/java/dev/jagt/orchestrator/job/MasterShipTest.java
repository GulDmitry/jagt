package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterShipTest {

    private final WorktreeChanges changes = mock(WorktreeChanges.class);
    private final CommandService commands = mock(CommandService.class);
    private final MasterOpen opener = mock(MasterOpen.class);

    @Test
    void shipsATaskThatHoldsWork() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.anyToShip(task)).thenReturn(true);

        boolean shipped = new MasterShip(changes, commands, opener).ship("ABC-1", task);

        assertThat(shipped).isTrue();
        verify(commands).execute("ABC-1", TaskAction.SHIP);
    }

    @Test
    void leavesATaskHoldingNothingToShipForTheHumanToClose() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();

        boolean shipped = new MasterShip(changes, commands, opener).ship("ABC-1", task);

        assertThat(shipped).isFalse();
        verify(commands, never()).execute(anyString(), any());
    }
}
