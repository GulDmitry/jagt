package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ActStops;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActCommandTest {

    private final ActStops actStops = mock(ActStops.class);

    @Test
    void saysTheMasterPressesAgainOnceTheStopWasLifted() {
        when(actStops.lift()).thenReturn(true);

        String answer = new ActCommand(actStops).run("");

        assertThat(answer).isEqualTo("act restored: the Master presses again where `master.mode` is act");
    }

    @Test
    void saysNothingWasStoppedWhereThereWasNoStopToLift() {
        when(actStops.lift()).thenReturn(false);

        String answer = new ActCommand(actStops).run("");

        assertThat(answer).isEqualTo("act was not stopped");
    }
}
