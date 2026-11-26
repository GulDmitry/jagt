package dev.jagt.orchestrator.service.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.protocol.debug.ScreenRead;
import dev.jagt.orchestrator.service.AgentSessions;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScreenReadsTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final ScreenReads reads = new ScreenReads(sessions);

  @Test
  void returnsOnlyTheLastLinesAsked() {
    when(sessions.screen("ABC-42")).thenReturn(Optional.of("one\ntwo\nthree"));

    assertThat(reads.screen(new ScreenRead("ABC-42", 2))).isEqualTo("two\nthree");
  }

  @Test
  void returnsWholeScreenWhereItHoldsFewerLinesThanAsked() {
    when(sessions.screen("ABC-42")).thenReturn(Optional.of("one\ntwo"));

    assertThat(reads.screen(new ScreenRead("ABC-42", null))).isEqualTo("one\ntwo");
  }

  @Test
  void saysTaskHasNoScreenWhereNoWindowHoldsIt() {
    when(sessions.screen("ABC-42")).thenReturn(Optional.empty());

    assertThat(reads.screen(new ScreenRead("ABC-42", null))).isEqualTo("ABC-42 has no screen");
  }
}
