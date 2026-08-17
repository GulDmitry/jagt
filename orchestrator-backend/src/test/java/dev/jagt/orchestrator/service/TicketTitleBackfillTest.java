package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.model.TicketFacts;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A task with no title reads as a bare id on the board, and this is what fills it in afterwards. */
class TicketTitleBackfillTest {

    private final TicketReader tickets = mock(TicketReader.class);
    private final StateService stateService = mock(StateService.class);
    private final TicketTitleBackfill backfill =
            new TicketTitleBackfill(tickets, stateService, Runnable::run);

    @Test
    void storesTheTitleAndTicketLinkItRead() {
        when(tickets.read("ABC-7")).thenReturn(new Answer<>(Optional.of(
                new TicketFacts(true, "ABC-7", "Widget layout is off", null, null, "https://tracker/ABC-7")),
                TokenUsage.NONE));

        backfill.of("ABC-7");

        ArgumentCaptor<UnaryOperator<TaskState>> update = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(stateService).updateTask(eq("ABC-7"), update.capture());
        TaskState titled = update.getValue().apply(
                TaskState.builder("group-a", "/w", TaskStatus.NEW).build());
        assertThat(titled.title()).isEqualTo("Widget layout is off");
        assertThat(titled.ticketUrl()).isEqualTo("https://tracker/ABC-7");
    }

    @Test
    void chargesTheReadToTheTaskEvenWhenItCameBackEmpty() {
        when(tickets.read("ABC-8")).thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

        backfill.of("ABC-8");

        verify(tickets).charge("ABC-8", TokenUsage.NONE);
        verify(stateService, never()).updateTask(any(), any());
    }

    @Test
    void leavesTheTaskAloneWhenTheReadThrows() {
        when(tickets.read("ABC-9")).thenThrow(new IllegalStateException("tracker down"));

        backfill.of("ABC-9");

        verify(stateService, never()).updateTask(any(), any());
    }
}
