package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedCausesTest {

    private final PipelineRetry pipelines = mock(PipelineRetry.class);
    private final InfraRetries retries = mock(InfraRetries.class);
    private final RedCauses causes = new RedCauses(pipelines, retries, InfraWaitProperties.defaults());
    private final ReviewFacts red = new ReviewFacts(true, false, "failed", "unit-tests", List.of(), 0, List.of(),
            List.of(), "https://host.invalid/p/1");

    @Test
    void waitsOutARunWhoseEveryFailedJobDiedOnInfrastructure() {
        when(pipelines.failedJobs("https://host.invalid/p/1")).thenReturn(new Answer<>(Optional.of(
                List.of(new FailedJob("1", "unit-tests", "runner_system_failure"))), TokenUsage.NONE));
        when(retries.arm("ABC-42", "https://host.invalid/p/1", "unit-tests", "runner_system_failure"))
                .thenReturn(Optional.of(InfraRetry.armed(9_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3)));
        when(retries.reading("ABC-42")).thenReturn("runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC");

        RedCauses.Verdict verdict = causes.judge("ABC-42", red, Pipeline.RED);

        assertThat(verdict.cause()).isEqualTo(RedCause.INFRASTRUCTURE);
        assertThat(verdict.retryLeft()).isTrue();
        assertThat(verdict.waiting()).isEqualTo("runner_system_failure: unit-tests · retry 1/3 at 10:05 UTC");
    }

    @Test
    void handsARunToTheSessionWhenOneFailedJobFailedItsScript() {
        when(pipelines.failedJobs("https://host.invalid/p/1")).thenReturn(new Answer<>(Optional.of(
                List.of(new FailedJob("1", "build", "runner_system_failure"), new FailedJob("2", "unit-tests", "script_failure"))),
                TokenUsage.NONE));

        RedCauses.Verdict verdict = causes.judge("ABC-42", red, Pipeline.RED);

        assertThat(verdict.cause()).isEqualTo(RedCause.CODE);
        verify(retries).clear("ABC-42", true);
        verify(retries, never()).arm(any(), any(), any(), any());
    }

    @Test
    void handsARunToTheSessionWhenItsFailedJobsCannotBeRead() {
        when(pipelines.failedJobs("https://host.invalid/p/1")).thenReturn(Answer.failed(TokenUsage.NONE,
                "glab not logged in to host.invalid"));

        RedCauses.Verdict verdict = causes.judge("ABC-42", red, Pipeline.RED);

        assertThat(verdict.cause()).isEqualTo(RedCause.CODE);
        assertThat(verdict.stop()).isEmpty();
        verify(retries, never()).arm(any(), any(), any(), any());
    }

    @Test
    void stopsTheTaskOnceEveryWaitWasSpent() {
        when(pipelines.failedJobs("https://host.invalid/p/1")).thenReturn(new Answer<>(Optional.of(
                List.of(new FailedJob("1", "unit-tests", "runner_system_failure"))), TokenUsage.NONE));
        when(retries.arm("ABC-42", "https://host.invalid/p/1", "unit-tests", "runner_system_failure"))
                .thenReturn(Optional.empty());
        TaskStop stop = new TaskStop(TaskStop.Trigger.INFRA, 1_000, "unit-tests: runner_system_failure, 3 retries");
        when(retries.exhausted("ABC-42")).thenReturn(Optional.of(stop));

        RedCauses.Verdict verdict = causes.judge("ABC-42", red, Pipeline.RED);

        assertThat(verdict.stop()).contains(stop);
        assertThat(verdict.retryLeft()).isFalse();
    }

    @Test
    void asksNothingOfTheHostWhenNoWaitIsConfigured() {
        RedCauses off = new RedCauses(pipelines, retries, new InfraWaitProperties(List.of()));

        RedCauses.Verdict verdict = off.judge("ABC-42", red, Pipeline.RED);

        assertThat(verdict.cause()).isEqualTo(RedCause.CODE);
        verifyNoInteractions(pipelines);
    }

    @Test
    void handsARunToTheSessionWhenTheReadGaveNoPipelineAddress() {
        ReviewFacts unaddressed = new ReviewFacts(true, false, "failed", "unit-tests", List.of(), 0, List.of(),
                List.of(), "");

        RedCauses.Verdict verdict = causes.judge("ABC-42", unaddressed, Pipeline.RED);

        assertThat(verdict.cause()).isEqualTo(RedCause.CODE);
        verifyNoInteractions(pipelines);
    }

    @Test
    void forgetsEveryRetryOnceTheRunWentGreen() {
        causes.judge("ABC-42", red, Pipeline.GREEN);

        verify(retries).clear("ABC-42", true);
    }

    @Test
    void forgetsOnlyTheArmedRetryWhileTheRunIsStillGoing() {
        causes.judge("ABC-42", red, Pipeline.RUNNING);

        verify(retries).clear("ABC-42", false);
    }
}
