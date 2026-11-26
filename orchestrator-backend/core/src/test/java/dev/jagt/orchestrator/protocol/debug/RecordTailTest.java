package dev.jagt.orchestrator.protocol.debug;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecordTailTest {

  @ParameterizedTest
  @ValueSource(strings = {"backend-log", "master-log", "stops", "stops-reviewed", "threads"})
  void acceptsEachRecordJagtKeeps(String record) {
    List<Violation> found = new RecordTail(record, null).violations(MessageContext.NONE);

    assertThat(found).isEmpty();
  }

  @Test
  void refusesRecordJagtDoesNotKeep() {
    List<Violation> found = new RecordTail("state.json", null).violations(MessageContext.NONE);

    assertThat(found).extracting(Violation::toString).anyMatch(text -> text.contains("record"));
  }

  @Test
  void refusesMoreLinesThanTheCeiling() {
    List<Violation> found = new RecordTail("stops", 1001).violations(MessageContext.NONE);

    assertThat(found).extracting(Violation::toString).anyMatch(text -> text.contains("lines"));
  }

  @Test
  void readsTwoHundredLinesWhenNoneAreAsked() {
    assertThat(new RecordTail("stops", null).count()).isEqualTo(200);
  }

  @Test
  void offersLinesAsAnOptionalInteger() {
    assertThat(RecordTail.SCHEMA.json())
        .contains("\"lines\":{\"type\":\"integer\"")
        .contains("\"required\":[\"record\"]");
  }
}
