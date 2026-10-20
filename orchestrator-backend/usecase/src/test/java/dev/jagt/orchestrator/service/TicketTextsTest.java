package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketTextsTest {

    private final TrackerAssistant assistant = mock(TrackerAssistant.class);
    private final UsageTracker usage = mock(UsageTracker.class);
    private final TicketTexts texts = new TicketTexts(assistant, usage);

    @Test
    void holdsTheTextOfATicketAsReadAndChargesTheReadToItsTask() {
        TokenUsage spent = TokenUsage.ofCall(9_000, 0, 80, 0.02);
        when(assistant.readTicketText("ABC-42")).thenReturn(new Answer<>(Optional.of("Widget layout is off"), spent));

        texts.read("ABC-42", "ABC-42", 1_000L);

        assertThat(texts.of("ABC-42")).contains("Widget layout is off");
        verify(usage).chargeTask("ABC-42", spent);
    }

    @Test
    void holdsNoTextForATaskNobodyReadAndAnswersNoReadSince() {
        assertThat(texts.of("ABC-42")).isEmpty();
        assertThat(texts.readSince("ABC-42", 0L)).isFalse();
    }

    @Test
    void saysWhetherATextWasReadAtOrAfterAMoment() {
        when(assistant.readTicketText("ABC-42")).thenReturn(new Answer<>(Optional.of("t"), TokenUsage.NONE));

        texts.read("ABC-42", "ABC-42", 1_000L);

        assertThat(texts.readSince("ABC-42", 1_000L)).isTrue();
        assertThat(texts.readSince("ABC-42", 1_001L)).isFalse();
    }
}
