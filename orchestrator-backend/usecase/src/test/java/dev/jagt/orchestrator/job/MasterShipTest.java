package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
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

    private final ShipDue due = mock(ShipDue.class);
    private final MasterPress press = mock(MasterPress.class);

    @Test
    void shipsATaskThatHoldsWork() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(due.due("ABC-1", task)).thenReturn(true);
        when(press.press("ABC-1", task, TaskAction.SHIP)).thenReturn(true);

        boolean shipped = new MasterShip(due, press).ship("ABC-1", task);

        assertThat(shipped).isTrue();
    }

    @Test
    void leavesATaskNoShipIsDueOnForTheHuman() {
        TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();

        boolean shipped = new MasterShip(due, press).ship("ABC-1", task);

        assertThat(shipped).isFalse();
        verify(press, never()).press(anyString(), any(), any());
    }
}
