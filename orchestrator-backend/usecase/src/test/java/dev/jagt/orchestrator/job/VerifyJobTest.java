package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestVerifyJobBuilder.verifyJob;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.ToldOnce;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyJobTest {

  private final Verification verification = mock(Verification.class);
  private final AgentStatusReports statusReports = mock(AgentStatusReports.class);
  private final StepRelay relay = mock(StepRelay.class);

  @TempDir Path root;

  private StateService state;
  private VerifyJob job;

  @BeforeEach
  void jobOverAnEmptyState() {
    state = stateService().withRoot(root).build();
    job = verifyJob().withRoot(root).build(state, verification, statusReports, relay);
  }

  @Test
  void handsTheRoundToTheHumanOnceTheProjectsOwnCommandPassed() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    when(verification.failure(any())).thenReturn(Optional.empty());

    job.runOn(state.tasks());

    verify(statusReports).verified(true, "verified", "ABC-1");
    verify(relay, never())
        .relayRound(eq("ABC-1"), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void sendsFailingRunBackToTheAgentWithItsOutputInsteadOfToTheHuman() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    when(verification.failure(any()))
        .thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));

    job.runOn(state.tasks());

    verify(relay)
        .relayRound(eq("ABC-1"), eq(Step.VERIFY), contains("BUILD FAILED"), any(Standing.class));
    verify(statusReports).verified(false, "verification failed; relayed", "ABC-1");
    verify(statusReports, never()).verified(eq(true), any(), any());
  }

  @Test
  void runsNoProbeForQuestionAtVerifying() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.VERIFYING)
            .report(AgentReport.QUESTION)
            .build());

    job.runOn(state.tasks());

    verify(verification, never()).failure(any());
    verify(relay, never())
        .relayRound(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Nested
  class WhereTheProjectsCommandFailedAndTheRoundRelays {

    @BeforeEach
    void commandFailing() {
      state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
      when(verification.failure(any()))
          .thenReturn(Optional.of("[proj] `./gradlew test` exited 1\nBUILD FAILED"));
      when(relay.relayRound(eq("ABC-1"), eq(Step.VERIFY), anyString(), any(Standing.class)))
          .thenReturn(true);
    }

    @Test
    void stopsTheTaskInsteadOfVerifyingOrRelayingTreeAgainAfterItsVerificationFailed() {
      when(verification.key(any())).thenReturn(Optional.of("tree-1"));
      job.runOn(state.tasks());

      job.runOn(state.tasks());

      verify(verification).failure(any());
      verify(relay, times(1))
          .relayRound(eq("ABC-1"), eq(Step.VERIFY), anyString(), any(Standing.class));
      verify(statusReports, never()).verified(eq(true), any(), any());
      TaskStop stop = state.task("ABC-1").orElseThrow().stop();
      assertThat(Objects.requireNonNull(stop).trigger())
          .isEqualTo(TaskStop.Trigger.UNCHANGED_FAILURE);
      assertThat(stop.evidence()).endsWith("[proj] `./gradlew test` exited 1\nBUILD FAILED");
    }

    @Test
    void verifiesChangedTreeAgainAfterFailedVerification() {
      when(verification.key(any())).thenReturn(Optional.of("tree-1"), Optional.of("tree-2"));
      job.runOn(state.tasks());

      job.runOn(state.tasks());

      verify(verification, times(2)).failure(any());
      verify(relay, times(2))
          .relayRound(eq("ABC-1"), eq(Step.VERIFY), anyString(), any(Standing.class));
    }

    @Test
    void keepsTaskStoppedAtItsRoundCeilingInVerifyingInsteadOfReportingTheRunFailed() {
      when(relay.relayRound(eq("ABC-1"), eq(Step.VERIFY), anyString(), any(Standing.class)))
          .thenAnswer(
              stopsTheTask -> {
                state.updateTask(
                    "ABC-1",
                    task ->
                        task.withStop(
                            new TaskStop(
                                TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds running")));
                return false;
              });

      job.runOn(state.tasks());

      verify(statusReports, never()).verified(eq(false), any(), any());
    }
  }

  @Test
  void storesNoMoreOfFailedRunsOutputThanTheStopCanShowPlusOneCharToFlagTheCut() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    when(verification.key(any())).thenReturn(Optional.of("tree-1"));
    when(verification.failure(any()))
        .thenReturn(Optional.of("a".repeat(4_001) + "b".repeat(5_999)));
    when(relay.relayRound(eq("ABC-1"), eq(Step.VERIFY), anyString(), any(Standing.class)))
        .thenReturn(true);

    job.runOn(state.tasks());

    String stored =
        state.task("ABC-1").orElseThrow().toldOrNone().valueOf(ToldOnce.Kind.VERIFY_OUTPUT);
    assertThat(stored).isEqualTo("a".repeat(4_001));
  }

  @Test
  void leavesTaskNobodyIsVerifyingAlone() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    job.runOn(state.tasks());

    verify(verification, never()).failure(any());
  }

  @Test
  void returnsTheRoundWithTheFailedProbeAsItsFindingAndRecordsEveryResult() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    Probe passed = Probe.specified(Probe.Kind.REF, List.of("main"), 0).probed(true, "exit 0");
    Probe failed =
        Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/guide.md"), 0)
            .probed(false, "no such file");
    when(verification.failure(any())).thenReturn(Optional.empty());
    when(verification.probed(eq("ABC-1"), any())).thenReturn(List.of(passed, failed));

    job.runOn(state.tasks());

    verify(relay)
        .relayRound(
            eq("ABC-1"),
            eq(Step.PROBES),
            eq("artifact docs/guide.md — FAILED\nno such file"),
            any(Standing.class));
    verify(statusReports).verified(false, "acceptance probe failed; relayed", "ABC-1");
    assertThat(state.task("ABC-1").orElseThrow().probes()).containsExactly(passed, failed);
  }

  @Nested
  class WhereAnAcceptanceProbeFailedAndTheRunPassed {

    private Probe failed;

    @BeforeEach
    void probeFailing() {
      state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
      failed =
          Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/guide.md"), 0)
              .probed(false, "no such file");
      when(verification.failure(any())).thenReturn(Optional.empty());
    }

    @Test
    void dropsFailedProbesWhenTheHumanShippedTheTaskMeanwhile() {
      when(verification.probed(eq("ABC-42"), any()))
          .thenAnswer(
              shipped -> {
                state.updateTask(
                    "ABC-42", task -> task.withStatus(TaskStatus.CI_POLLING, null, 5L));
                return List.of(failed);
              });

      job.runOn(state.tasks());

      verify(relay, never())
          .relayRound(anyString(), any(Step.class), anyString(), any(Standing.class));
      verify(statusReports, never()).verified(anyBoolean(), any(), any());
      assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
      assertThat(state.task("ABC-42").orElseThrow().probes()).isEmpty();
    }

    @Test
    void relaysFailedProbesWhileTheTaskIsStillOnTheRoundTheyWereTakenFor() {
      when(verification.probed(eq("ABC-42"), any())).thenReturn(List.of(failed));

      job.runOn(state.tasks());

      verify(relay)
          .relayRound(
              "ABC-42",
              Step.PROBES,
              "artifact docs/guide.md — FAILED\nno such file",
              Standing.of(state.task("ABC-42").orElseThrow()));
      verify(statusReports).verified(false, "acceptance probe failed; relayed", "ABC-42");
    }
  }
}
