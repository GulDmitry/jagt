package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.LoopGuard;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoopStopGuardTest {

    private final LoopGuard loops = mock(LoopGuard.class);

    @Test
    void refusesTheMastersShipOnAStoppedTask() {
        when(loops.stopped("ABC-42")).thenReturn(true);
        LoopStopGuard guard = new LoopStopGuard(TaskAction.SHIP, loops);

        assertThatThrownBy(() -> OriginContext.as(ActionOrigin.MASTER,
                () -> guard.around("ABC-42", () -> Outcome.ok("shipped")))).isInstanceOf(Refusal.class);
    }

    @Test
    void runsTheHumansOwnPressOnAStoppedTask() {
        when(loops.stopped("ABC-42")).thenReturn(true);
        LoopStopGuard guard = new LoopStopGuard(TaskAction.DEPLOY, loops);

        Outcome outcome = OriginContext.as(ActionOrigin.BOARD, () -> guard.around("ABC-42", () -> Outcome.ok("deployed")));

        assertThat(outcome.message()).isEqualTo("deployed");
    }
}
