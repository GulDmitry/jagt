package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotesOpenWithTheAiLineTest {

  @Test
  void actorNoteWithoutTheAiLineIsViolation() {
    RunRecord run =
        TestRunRecordBuilder.runRecord()
            .withNotes(
                List.of(new RunRecord.Note(1, 101, "actor", "Looks fine to me", Instant.EPOCH)))
            .build();

    List<Violation> violations = new NotesOpenWithTheAiLine().check(run);

    assertThat(violations)
        .singleElement()
        .satisfies(
            violation -> assertThat(violation.evidence()).startsWith("note 1 on request 101"));
  }

  @Test
  void personsNoteWithoutTheAiLineIsNoViolation() {
    RunRecord run =
        TestRunRecordBuilder.runRecord()
            .withNotes(
                List.of(new RunRecord.Note(1, 101, "person", "Looks fine to me", Instant.EPOCH)))
            .build();

    assertThat(new NotesOpenWithTheAiLine().check(run)).isEmpty();
  }
}
