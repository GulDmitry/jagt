package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.ProjectRouting;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeStartTest {

    private final ProjectRouting routing = mock(ProjectRouting.class);
    private final IntakeHistory history = mock(IntakeHistory.class);
    private final TaskLauncher launcher = mock(TaskLauncher.class);
    private final IntakeStart starts = new IntakeStart(routing, history, launcher);

    @Test
    void startsTheTaskInTheProjectTheItemWasPlacedIn() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withLabels(List.of("backend"));
        when(routing.projectFor(item)).thenReturn(new ProjectRouting.Placed("api"));
        when(launcher.launch(any(), any())).thenReturn(Launched.created("ABC-42", "started"));

        starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

        verify(launcher).launch(LaunchRequest.of("ABC-42").withProject("api"),
                new Answer<>(Optional.of(item), TokenUsage.NONE));
    }

    @Test
    void turnsAwayAnItemNothingCouldPlaceInAProject() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42");
        when(routing.projectFor(item)).thenReturn(new ProjectRouting.Undecided("placed in no configured project"));

        starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

        verify(history).turnAway(eq("ABC-42"), anyString());
        verify(launcher, never()).launch(any(), any());
    }

    @Test
    void stopsThePollWhereTheBoardRefusedTheLaunch() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-1");
        when(routing.projectFor(item)).thenReturn(new ProjectRouting.Placed("api"));
        when(launcher.launch(any(), any()))
                .thenThrow(new IllegalArgumentException("24 tasks are already open, which is the limit"));

        boolean goesOn = starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

        assertThat(goesOn).isFalse();
    }

    @Test
    void leavesAnItemTheRouterCouldNotAnswerForTheNextPollInsteadOfTurningItAway() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42");
        when(routing.projectFor(item)).thenReturn(new ProjectRouting.Unreadable("the router answered nothing"));

        boolean goesOn = starts.start(new IntakeCandidates.Ready(item, TokenUsage.NONE));

        assertThat(goesOn).isFalse();
        verify(history, never()).turnAway(anyString(), anyString());
        verify(launcher, never()).launch(any(), any());
    }
}
