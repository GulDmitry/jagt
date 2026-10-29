package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.service.ReviewSweepService;
import dev.jagt.orchestrator.service.RoundReading;
import dev.jagt.orchestrator.service.RoundRecord;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ReviewFacts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StopReadsTest {

    private final RoundReading reading = mock(RoundReading.class);
    private final TaskStops stops = mock(TaskStops.class);
    private final RoundRecord record = mock(RoundRecord.class);
    private final StopReads reads = new StopReads(reading, stops, record);

    @Test
    void offersTheStopTheCheckVerdictOfARequestItCouldRead() {
        when(reading.read("ABC-42")).thenReturn(new RoundReading.Round("http://mr/1", new ReviewFacts(
                true, false, "success", "", List.of(), 0, List.of(), List.of(), "", false), ""));

        reads.probe("ABC-42");

        verify(stops).endOnRead("ABC-42", Pipeline.GREEN);
        verifyNoMoreInteractions(stops);
    }

    @Test
    void keepsTheStopWhereTheRequestCouldNotBeRead() {
        when(reading.read("ABC-42")).thenReturn(new RoundReading.Refused(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed")));

        reads.probe("ABC-42");

        verifyNoInteractions(record);
        verify(stops, never()).endOnRead(any(), any());
    }

    @Test
    void keepsWhatItReadOnTheTaskWhereTheReadEndsTheStop() {
        ReviewFacts green = new ReviewFacts(true, true, "success", "", List.of(), 0, List.of(), List.of(), "", false);
        when(reading.read("ABC-42")).thenReturn(new RoundReading.Round("http://mr/1", green, ""));

        reads.probe("ABC-42");

        verify(record).recordListed("ABC-42", green);
    }

    @Test
    void asksTheStopToEndWhereTheHostSaysTheRequestIsGone() {
        when(reading.read("ABC-42")).thenReturn(new RoundReading.Refused(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.GONE, "error: no such request")));

        reads.probe("ABC-42");

        verify(stops).endOnRefusal("ABC-42", ReviewSweepService.SweepResult.Kind.GONE);
    }
}
