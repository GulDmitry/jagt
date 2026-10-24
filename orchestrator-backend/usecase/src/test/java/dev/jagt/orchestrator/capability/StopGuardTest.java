package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StopGuardTest {

    private final TaskStops stops = mock(TaskStops.class);

    @Test
    void refusesTheMastersShipOnAStoppedTask() {
        when(stops.stopped("ABC-42")).thenReturn(true);
        StopGuard guard = new StopGuard(TaskAction.SHIP, stops);

        assertThatThrownBy(() -> OriginContext.as(ActionOrigin.MASTER,
                () -> guard.around("ABC-42", () -> Outcome.ok("shipped")))).isInstanceOf(Refusal.class);
    }

    @Test
    void runsTheHumansOwnPressOnAStoppedTask() {
        when(stops.stopped("ABC-42")).thenReturn(true);
        StopGuard guard = new StopGuard(TaskAction.DEPLOY, stops);

        Outcome outcome = OriginContext.as(ActionOrigin.BOARD, () -> guard.around("ABC-42", () -> Outcome.ok("deployed")));

        assertThat(outcome.message()).isEqualTo("deployed");
    }
}
