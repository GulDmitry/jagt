package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RoundRecordTest {

    private final StateService stateService = mock(StateService.class);
    private final Notifications notifications = mock(Notifications.class);
    private final RoundRecord roundRecord = new RoundRecord(stateService, notifications);

    @Test
    void keepsAnEarlierRoundsVerdictFlaggedWhenThisSweepCouldNotReadTheChecks() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build()));
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "unknown", List.of()));

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

        roundRecord.record("ABC-1", new ReviewFacts(true, true, "success", List.of()));

        verify(stateService).updateTask(eq("ABC-1"), stamped.capture());
        assertThat(stamped.getValue()
                .apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()).approved()).isTrue();
    }

    @Test
    void keepsWhatTheHostSaidAboutTheChecksOnTheTask() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).build()));
        ArgumentCaptor<UnaryOperator<TaskState>> stamped = ArgumentCaptor.captor();

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "SUCCEEDED", List.of()));

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

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "running", List.of(), 1_700_000_000_000L));

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

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "running", List.of()));

        verify(stateService, never()).updateTask(eq("ABC-1"), any());
    }

    @Test
    void tapsTheHumanWhenTheChecksGoRedAndSaysNothingOnALaterPollOfTheSameRun() {
        AtomicReference<TaskState> stored = new AtomicReference<>(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        when(stateService.task("ABC-1")).thenAnswer(call -> Optional.of(stored.get()));
        when(stateService.updateTask(eq("ABC-1"), any())).thenAnswer(call -> {
            stored.set(call.<UnaryOperator<TaskState>>getArgument(1).apply(stored.get()));
            return true;
        });

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "failed", List.of()));
        roundRecord.record("ABC-1", new ReviewFacts(true, false, "failed", List.of()));

        verify(notifications, times(1)).send(Notification.checksFailed("ABC-1", "failed"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void saysNothingWhenAFailedRunComesBackGreen() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build()));

        roundRecord.record("ABC-1", new ReviewFacts(true, false, "success", List.of()));

        verifyNoInteractions(notifications);
    }

    @Test
    void tellsTheHumanNothingAboutAGreenSweep() {
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState
                .builder("proj", "/wt", TaskStatus.CI_POLLING).build()));

        roundRecord.record("ABC-1", new ReviewFacts(true, true, "success", List.of()));

        verifyNoInteractions(notifications);
    }
}
