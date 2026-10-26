package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
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
import static org.mockito.Mockito.times;
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
        verify(relay, never()).relayRound(eq("ABC-1"), anyString(), anyString());
    }

    @Test
    void sendsAFailingRunBackToTheAgentWithItsOutputInsteadOfToTheHuman(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.failure(any())).thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(relay).relayRound(eq("ABC-1"), eq("verify"), contains("BUILD FAILED"));
        verify(statusReports).verified(false, "verification failed; relayed", "ABC-1");
        verify(statusReports, never()).verified(eq(true), any(), any());
    }

    @Test
    void runsNoProbeForAQuestionAtVerifying(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING)
                .report(AgentReport.QUESTION).build());

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(verification, never()).failure(any());
        verify(relay, never()).relayRound(anyString(), anyString(), anyString());
    }

    @Test
    void doesNotVerifyOrRelayATreeAgainAfterItsVerificationFailed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.key(any())).thenReturn(Optional.of("tree-1"));
        when(verification.failure(any())).thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));
        when(relay.relayRound(eq("ABC-1"), eq("verify"), anyString())).thenReturn(true);
        new VerifyJob(state, verification, statusReports, relay).run();

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(verification, times(1)).failure(any());
        verify(relay, times(1)).relayRound(eq("ABC-1"), eq("verify"), anyString());
        verify(statusReports).verified(true, "the tree is unchanged since its verification failed; not run again",
                "ABC-1");
    }

    @Test
    void verifiesAChangedTreeAgainAfterAFailedVerification(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.key(any())).thenReturn(Optional.of("tree-1"), Optional.of("tree-2"));
        when(verification.failure(any())).thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));
        when(relay.relayRound(eq("ABC-1"), eq("verify"), anyString())).thenReturn(true);
        new VerifyJob(state, verification, statusReports, relay).run();

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(verification, times(2)).failure(any());
        verify(relay, times(2)).relayRound(eq("ABC-1"), eq("verify"), anyString());
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

        verify(relay).relayRound(eq("ABC-1"), eq("probes"), eq("artifact docs/guide.md — FAILED\nno such file"));
        verify(statusReports).verified(false, "acceptance probe failed; relayed", "ABC-1");
        assertThat(state.task("ABC-1").orElseThrow().probes()).containsExactly(passed, failed);
    }

    @Test
    void keepsATaskStoppedAtItsRoundCeilingInVerifyingInsteadOfReportingTheRunFailed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
        when(verification.failure(any())).thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));
        when(relay.relayRound(eq("ABC-1"), eq("verify"), anyString())).thenAnswer(stopsTheTask -> {
            state.updateTask("ABC-1", task -> task.withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 5,
                    "verify sent 3 rounds running")));
            return false;
        });

        new VerifyJob(state, verification, statusReports, relay).run();

        verify(statusReports, never()).verified(eq(false), any(), any());
    }
}
