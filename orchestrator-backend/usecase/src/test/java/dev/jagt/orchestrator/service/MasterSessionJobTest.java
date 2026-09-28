package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MasterSessionJobTest {

    private final MasterSession master = mock(MasterSession.class);
    private final MasterSessionJob job = new MasterSessionJob(master, mock(MasterSpend.class));

    @Test
    void endsASessionThatOutlivedTheBackendBeforeStartingOne() {
        job.run();

        inOrder(master).verify(master).stop();
        inOrder(master).verify(master).startIfWanted();
    }

    @Test
    void leavesTheSessionItStartedAloneOnEveryLaterTick() {
        job.run();
        job.run();
        job.run();

        verify(master, times(1)).stop();
        verify(master, times(3)).startIfWanted();
    }
}
