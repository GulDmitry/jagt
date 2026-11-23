package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunRecordNoteTest {

  @Test
  void noteOfKindActorIsByTheActor() {
    assertThat(new RunRecord.Note(1, 101, "actor", "text", Instant.EPOCH).byActor()).isTrue();
  }

  @Test
  void noteOfKindBotIsNotByTheActor() {
    assertThat(new RunRecord.Note(1, 101, "bot", "text", Instant.EPOCH).byActor()).isFalse();
  }
}
