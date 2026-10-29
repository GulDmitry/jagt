package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.InfraRetries;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfraRetryJobTest {

    private final InfraRetries retries = mock(InfraRetries.class);
    private final PipelineRetry pipelines = mock(PipelineRetry.class);
    private final TaskStops stops = mock(TaskStops.class);
    private final InfraRetryJob job = new InfraRetryJob(retries, pipelines, stops);
    private final InfraRetry armed = InfraRetry.armed(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);

    @Test
    void reRunsAPipelineWhoseFailedJobsStillDiedOnInfrastructure() {
        when(retries.due()).thenReturn(Map.of("ABC-42", armed));
        when(retries.stillWaiting("ABC-42")).thenReturn(true);
        when(stops.moveDue("ABC-42")).thenReturn(true);
        when(pipelines.failedJobs("https://host.invalid/p/1")).thenReturn(new Answer<>(Optional.of(
                List.of(new FailedJob("1", "unit-tests", "runner_system_failure"))), TokenUsage.NONE));
        when(pipelines.retryFailed("https://host.invalid/p/1")).thenReturn(new Answer<>(Optional.of("pending"), TokenUsage.NONE));

        job.run();

        verify(retries).sent("ABC-42", armed);
    }

    @Test
    void dropsTheRetryWhenAHumanAlreadyMovedTheChecksOnFromRed() {
        when(retries.due()).thenReturn(Map.of("ABC-42", armed));
        when(retries.stillWaiting("ABC-42")).thenReturn(false);

        job.run();

        verify(retries).clear("ABC-42", false);
        verify(pipelines, never()).retryFailed("https://host.invalid/p/1");
    }

    @Test
    void dropsTheRetryOfATaskThatWasClosed() {
        when(retries.due()).thenReturn(Map.of("ABC-42", armed));
        when(retries.stillWaiting("ABC-42")).thenReturn(false);

        job.run();

        verify(retries).clear("ABC-42", false);
        verify(pipelines, never()).retryFailed("https://host.invalid/p/1");
    }

    @Test
    void dropsTheRetryOfAStoppedTask() {
        when(retries.due()).thenReturn(Map.of("ABC-42", armed));
        when(retries.stillWaiting("ABC-42")).thenReturn(true);
        when(stops.moveDue("ABC-42")).thenReturn(false);

        job.run();

        verify(retries).clear("ABC-42", false);
    }

    @Test
    void dropsTheRetryWhenTheHostNowSaysTheCodeFailed() {
        when(retries.due()).thenReturn(Map.of("ABC-42", armed));
        when(retries.stillWaiting("ABC-42")).thenReturn(true);
        when(stops.moveDue("ABC-42")).thenReturn(true);
        when(pipelines.failedJobs("https://host.invalid/p/1")).thenReturn(new Answer<>(Optional.of(
                List.of(new FailedJob("1", "unit-tests", "script_failure"))), TokenUsage.NONE));

        job.run();

        verify(retries).clear("ABC-42", false);
        verify(pipelines, never()).retryFailed("https://host.invalid/p/1");
    }

    @Test
    void stopsTheTaskWithTheHostsOwnWordsWhenItRefusesTheRetry() {
        when(retries.due()).thenReturn(Map.of("ABC-42", armed));
        when(retries.stillWaiting("ABC-42")).thenReturn(true);
        when(stops.moveDue("ABC-42")).thenReturn(true);
        when(pipelines.failedJobs("https://host.invalid/p/1")).thenReturn(new Answer<>(Optional.of(
                List.of(new FailedJob("1", "unit-tests", "runner_system_failure"))), TokenUsage.NONE));
        when(pipelines.retryFailed("https://host.invalid/p/1")).thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1: 403 Forbidden"));

        job.run();

        verify(retries).refused("ABC-42", "glab exited 1: 403 Forbidden");
        verify(retries, never()).sent("ABC-42", armed);
    }
}
