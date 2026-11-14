package dev.jagt.orchestrator.service.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.service.AgentSessions;
import org.junit.jupiter.api.Test;

class ColdPromptsTest {

  private final AgentSessions sessions = mock(AgentSessions.class);

  @Test
  void handsPromptToFreshSessionWhichCarriesWhatTheOldOneKnew() {
    new ColdPrompts(sessions).relay("ABC-42", "now handle empty files");

    verify(sessions).relayUnderCold("ABC-42", "now handle empty files");
  }
}
