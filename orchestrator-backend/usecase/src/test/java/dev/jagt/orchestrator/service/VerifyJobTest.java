package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifyJobTest {

    private final StateService stateService = mock(StateService.class);
    private final Verification verification = mock(Verification.class);
    private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final VerifyJob job = new VerifyJob(stateService, verification, statusReports, sessions);

    @Test
    void handsTheRoundToTheHumanOnceTheProjectsOwnCommandPassed() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1").build();
        when(stateService.tasks()).thenReturn(Map.of("ABC-1", task));
        when(verification.failure(task)).thenReturn(Optional.empty());

        job.run();

        verify(statusReports).report(TaskStatus.REVIEW_PENDING, "verified", "ABC-1");
        verify(sessions, never()).relayIfChanged(eq("ABC-1"), any());
    }

    @Test
    void sendsAFailingRunBackToTheAgentWithItsOutputInsteadOfToTheHuman() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1").build();
        when(stateService.tasks()).thenReturn(Map.of("ABC-1", task));
        when(verification.failure(task)).thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));
        ArgumentCaptor<String> relayed = ArgumentCaptor.captor();

        job.run();

        verify(sessions).relayIfChanged(eq("ABC-1"), relayed.capture());
        assertThat(relayed.getValue()).contains("BUILD FAILED");
        verify(statusReports).report(TaskStatus.IN_PROGRESS, "verification failed; relayed", "ABC-1");
        verify(statusReports, never()).report(eq(TaskStatus.REVIEW_PENDING), any(), any());
    }

    @Test
    void leavesATaskNobodyIsVerifyingAlone() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));

        job.run();

        verify(verification, never()).failure(any());
    }
}
