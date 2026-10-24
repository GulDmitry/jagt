package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ChecksUnread;
import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoundRecordTest {

    private final StateService stateService = mock(StateService.class);
    private final TaskStops stops = mock(TaskStops.class);
    private final RoundRecord roundRecord = new RoundRecord(stateService, stops,
            Clock.fixed(Instant.ofEpochMilli(3_000), ZoneOffset.UTC));

    @Test
    void stopsTheTaskWhenTheThirdReadRunningCouldNotListTheChecks() {
        ArgumentCaptor<Function<TaskState, LoopWatch>> tripped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "unknown", "list_pipelines: 403 Forbidden", List.of(), 0, List.of()));

        verify(stops).trip(eq("ABC-1"), tripped.capture());
        LoopWatch watch = tripped.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()
                .withChecksUnread(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"))
                .withChecksUnread(new ChecksUnread(2_000, "list_pipelines: 403 Forbidden"))
                .withChecksUnread(new ChecksUnread(3_000, "list_pipelines: 403 Forbidden")));
        assertThat(watch.stop()).isNotNull();
        assertThat(watch.stop().trigger()).isEqualTo(LoopWatch.Trigger.CHECKS);
    }

    @Test
    void keepsTheReadsOwnWordsForWhyItCouldNotListTheChecks() {
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "unknown", "list_pipelines: 403 Forbidden", List.of(), 0, List.of()));

        verify(stateService).updateTask(eq("ABC-1"), stamped.capture());
        assertThat(stamped.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
                .checksUnread()).containsExactly(new ChecksUnread(3_000, "list_pipelines: 403 Forbidden"));
    }

    @Test
    void keepsAnEarlierRoundsVerdictFlaggedWhenThisSweepCouldNotReadTheChecks() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build()));
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "unknown", "", List.of(), 0, List.of()));

        verify(stateService).updateTask(eq("ABC-1"), stamped.capture());
        TaskState after = stamped.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .pipelineStatus("failed").build());
        assertThat(after.pipelineStatus()).isEqualTo("failed");
        assertThat(after.pipelineUnread()).isTrue();
    }

    @Test
    void stampsWhetherTheRoundIsApprovedSoBothSurfacesCanShowItBesideTheRequest() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).build()));
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, true, "success", "", List.of(), 0, List.of()));

        verify(stateService).updateTask(eq("ABC-1"), stamped.capture());
        assertThat(stamped.getValue()
                .apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()).approved()).isTrue();
    }

    @Test
    void keepsWhatTheHostSaidAboutTheChecksOnTheTask() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).build()));
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "SUCCEEDED", "", List.of(), 0, List.of()));

        verify(stateService).updateTask(eq("ABC-1"), stamped.capture());
        assertThat(stamped.getValue()
                .apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
                .pipelineStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void keepsWhenTheHostSaysTheRequestWasOpened() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).build()));
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "running", "", List.of(), 1_700_000_000_000L, List.of()));

        verify(stateService).updateTask(eq("ABC-1"), stamped.capture());
        assertThat(stamped.getValue()
                .apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
                .requestOpenedAt()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void leavesTheRequestsAgeAloneWhenTheReadCouldNotSayWhenItWasOpened() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("running").approved(false)
                .requestOpenedAt(1_700_000_000_000L).build()));

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "running", "", List.of(), 0, List.of()));

        verify(stateService, never()).updateTask(eq("ABC-1"), any());
    }

    @Test
    void keepsTheNewAgeOfTheRequestWhenNothingElseAboutTheRoundChanged() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("running").approved(false)
                .requestOpenedAt(1_700_000_000_000L).build()));
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "running", "", List.of(), 1_700_000_500_000L, List.of()));

        verify(stateService).updateTask(eq("ABC-1"), stamped.capture());
        assertThat(stamped.getValue()
                .apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
                .requestOpenedAt()).isEqualTo(1_700_000_500_000L);
    }
}
