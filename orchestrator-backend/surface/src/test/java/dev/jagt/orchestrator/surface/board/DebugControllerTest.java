package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.service.log.DebugReads;
import dev.jagt.orchestrator.surface.ui.log.LogReads;
import org.junit.jupiter.api.Test;

class DebugControllerTest {

  private final LogReads logs = mock(LogReads.class);
  private final DebugReads reads = mock(DebugReads.class);
  private final DebugController api = new DebugController(logs, reads);

  @Test
  void readsTheTailOfTheKnownRecord() {
    when(logs.read(new RecordTail("stops", 50))).thenReturn("a stop line");

    assertThat(api.record("stops", 50)).isEqualTo("a stop line");
  }

  @Test
  void refusesTheRecordJagtDoesNotKeepRatherThanReadingIt() {
    assertThatThrownBy(() -> api.record("passwd", null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(logs);
  }

  @Test
  void readsTheDefaultNumberOfTranscriptCharactersWhereNoneIsAsked() {
    when(reads.transcript("ABC-1", 16_000)).thenReturn("the transcript");

    assertThat(api.transcript("ABC-1", null)).isEqualTo("the transcript");
  }
}
