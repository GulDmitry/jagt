package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifyJobTest {

    private final Verification verification = mock(Verification.class);
    private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void handsTheRoundToTheHumanOnceTheProjectsOwnCommandPassed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.failure(any())).thenReturn(Optional.empty());

        new VerifyJob(state, verification, statusReports, sessions).run();

        verify(statusReports).report(TaskStatus.REVIEW_PENDING, "verified", "ABC-1");
        verify(sessions, never()).relayIfChanged(eq("ABC-1"), any());
    }

    @Test
    void sendsAFailingRunBackToTheAgentWithItsOutputInsteadOfToTheHuman(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.failure(any())).thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));
        ArgumentCaptor<String> relayed = ArgumentCaptor.captor();

        new VerifyJob(state, verification, statusReports, sessions).run();

        verify(sessions).relayIfChanged(eq("ABC-1"), relayed.capture());
        assertThat(relayed.getValue()).contains("BUILD FAILED");
        verify(statusReports).report(TaskStatus.IN_PROGRESS, "verification failed; relayed", "ABC-1");
        verify(statusReports, never()).report(eq(TaskStatus.REVIEW_PENDING), any(), any());
    }

    @Test
    void leavesATaskNobodyIsVerifyingAlone(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

        new VerifyJob(state, verification, statusReports, sessions).run();

        verify(verification, never()).failure(any());
    }
}
