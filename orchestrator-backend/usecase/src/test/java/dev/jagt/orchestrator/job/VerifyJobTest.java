package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifyJobTest {

    private final Verification verification = mock(Verification.class);
    private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
    private final StepRelay relay = mock(StepRelay.class);

    @Test
    void handsTheRoundToTheHumanOnceTheProjectsOwnCommandPassed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.failure(any())).thenReturn(Optional.empty());

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(statusReports).verified(true, "verified", "ABC-1");
        verify(relay, never()).relay(eq("ABC-1"), anyString(), anyString());
    }

    @Test
    void sendsAFailingRunBackToTheAgentWithItsOutputInsteadOfToTheHuman(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.failure(any())).thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(relay).relay(eq("ABC-1"), eq("verify"), contains("BUILD FAILED"));
        verify(statusReports).verified(false, "verification failed; relayed", "ABC-1");
        verify(statusReports, never()).verified(eq(true), any(), any());
    }

    @Test
    void leavesATaskNobodyIsVerifyingAlone(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(verification, never()).failure(any());
    }

    @Test
    void returnsTheRoundWithTheFailedProbeAsItsFindingAndRecordsEveryResult(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        Probe passed = Probe.specified(Probe.Kind.REF, List.of("main"), 0).probed(true, "exit 0");
        Probe failed = Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/guide.md"), 0).probed(false, "no such file");
        when(verification.failure(any())).thenReturn(Optional.empty());
        when(verification.probed(eq("ABC-1"), any())).thenReturn(List.of(passed, failed));

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(relay).relay(eq("ABC-1"), eq("probes"), eq("artifact docs/guide.md — FAILED\nno such file"));
        verify(statusReports).verified(false, "acceptance probe failed; relayed", "ABC-1");
        assertThat(state.task("ABC-1").orElseThrow().probes()).containsExactly(passed, failed);
    }
}
