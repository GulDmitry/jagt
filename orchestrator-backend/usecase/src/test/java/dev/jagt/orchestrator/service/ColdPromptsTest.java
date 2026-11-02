package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ColdPromptsTest {

    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void handsAPromptToAFreshSessionWhichCarriesWhatTheOldOneKnew() {
        new ColdPrompts(sessions).relay("ABC-42", "now handle empty files");

        verify(sessions).relayUnderCold("ABC-42", "now handle empty files");
    }
}
