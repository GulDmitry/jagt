package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackerCloseTest {

    private final TicketReader tickets = mock(TicketReader.class);
    private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
    private final WorktreeChanges changes = mock(WorktreeChanges.class);
    private final TrackerClose close = new TrackerClose(tickets, workflow, changes);

    @Test
    void closesATaskWhoseItemLandedAndWhoseWorktreeHoldsNothingUncommitted() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget")
                .withUrl("https://tracker/ABC-42").withTrackerStatus("Ready for Stage");
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(workflow.closesWork(item)).thenReturn(true);
        when(changes.uncommitted(task)).thenReturn(Optional.of(false));

        assertThat(close.closes("ABC-42", task)).isTrue();
    }

    @Test
    void refusesToCloseOverWorkNobodyHasCommitted() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget")
                .withUrl("https://tracker/ABC-42").withTrackerStatus("Ready for Stage");
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(workflow.closesWork(item)).thenReturn(true);
        when(changes.uncommitted(task)).thenReturn(Optional.of(true));

        assertThat(close.closes("ABC-42", task)).isFalse();
    }

    @Test
    void refusesToCloseWhenGitCouldNotSayWhatTheWorktreeHolds() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget")
                .withUrl("https://tracker/ABC-42").withTrackerStatus("Ready for Stage");
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
        when(workflow.closesWork(item)).thenReturn(true);
        when(changes.uncommitted(task)).thenReturn(Optional.empty());

        assertThat(close.closes("ABC-42", task)).isFalse();
    }

    @Test
    void refusesToCloseOnAStageNobodyCouldRead() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

        assertThat(close.closes("ABC-42", task)).isFalse();
        verify(changes, never()).uncommitted(any());
    }
}
