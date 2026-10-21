package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.PressLog;
import dev.jagt.orchestrator.service.PressLog.Interaction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InteractionsControllerTest {

    private final PressLog presses = mock(PressLog.class);

    @Test
    void recordsTheBatchOfPressesThePageSent() {
        List<Interaction> sent = List.of(new Interaction(1_000L, "verb:ship", "REVIEW_PENDING"));

        new InteractionsController(presses, Clock.systemUTC()).record(new InteractionsController.Batch(sent));

        verify(presses).record(eq(sent), anyLong());
    }

    @Test
    void recordsAnEmptyBatchForABodyCarryingNoPresses() {
        new InteractionsController(presses, Clock.systemUTC()).record(new InteractionsController.Batch(null));

        verify(presses).record(eq(List.of()), anyLong());
    }
}
