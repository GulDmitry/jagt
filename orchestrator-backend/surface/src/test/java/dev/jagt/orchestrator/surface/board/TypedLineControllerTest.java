package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.AgentSessions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TypedLineControllerTest {

    private final NaturalLanguageDispatch naturalLanguage = mock(NaturalLanguageDispatch.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final TypedLineController api = new TypedLineController(naturalLanguage, sessions);

    @Test
    void saysALineToTheSessionOfTheTaskTheReportIsAbout() {
        when(sessions.say("a1", "no, answer 2 differently")).thenReturn("Said to the agent.");

        assertThat(api.say("a1", new TypedLineController.LineRequest("no, answer 2 differently")).message())
                .isEqualTo("Said to the agent.");
    }

    @Test
    void refusesAnEmptyLineRatherThanInterruptingASessionWithNothing() {
        assertThatThrownBy(() -> api.say("a1", new TypedLineController.LineRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing to say");
        verifyNoInteractions(sessions);
    }

    @Test
    void passesPaletteTextToTheDispatcherAndReturnsItsAnswerUnchanged() {
        when(naturalLanguage.interpret("ship the login task"))
                .thenReturn("understood as `ship a2` — ship a2: pushed");

        assertThat(api.interpret(new TypedLineController.InterpretRequest("ship the login task")).message())
                .isEqualTo("understood as `ship a2` — ship a2: pushed");
        verifyNoInteractions(sessions);
    }
}
