package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketPrefetchJobTest {

    private final StateService state = mock(StateService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);
    private final TicketTexts tickets = new TicketTexts(assistant);
    private final TicketPrefetchJob job = new TicketPrefetchJob(state, config, tickets);

    @Test
    void holdsAWorkingTasksTicketForItsNextReview() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .ticketUrl("https://tracker.example/ABC-42").build()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(assistant.readTicketText("https://tracker.example/ABC-42"))
                .thenReturn(new Answer<>(Optional.of("Summary: accept v3 calls"), TokenUsage.NONE));

        job.run();

        assertThat(tickets.of("ABC-42")).contains("Summary: accept v3 calls");
    }

    @Test
    void asksOncePerStintEvenWhereTheReadFailed() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .ticketUrl("https://tracker.example/ABC-42").build()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(assistant.readTicketText("https://tracker.example/ABC-42")).thenReturn(Answer.unavailable());

        job.run();
        job.run();

        verify(assistant, times(1)).readTicketText("https://tracker.example/ABC-42");
    }

    @Test
    void readsNothingWhereNoMasterWillReview() {
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .ticketUrl("https://tracker.example/ABC-42").build()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

        job.run();

        verify(assistant, never()).readTicketText("https://tracker.example/ABC-42");
    }
}
