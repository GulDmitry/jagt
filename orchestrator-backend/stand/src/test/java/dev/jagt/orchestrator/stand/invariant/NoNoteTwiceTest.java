package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoNoteTwiceTest {

  @Test
  void secondActorNoteWithTheSameKeyOnTheSameRequestIsViolation() {
    RunRecord run =
        TestRunRecordBuilder.runRecord()
            .withNotes(
                List.of(
                    new RunRecord.Note(
                        1, 101, "actor", "a <!-- jagt-review:0a1b -->", Instant.EPOCH),
                    new RunRecord.Note(
                        2, 101, "actor", "a <!-- jagt-review:0a1b -->", Instant.EPOCH)))
            .build();

    List<Violation> violations = new NoNoteTwice().check(run);

    assertThat(violations)
        .singleElement()
        .satisfies(violation -> assertThat(violation.evidence()).startsWith("note 2 repeats"));
  }

  @Test
  void sameKeyOnTwoRequestsIsNoRepeat() {
    RunRecord run =
        TestRunRecordBuilder.runRecord()
            .withNotes(
                List.of(
                    new RunRecord.Note(
                        1, 101, "actor", "a <!-- jagt-review:0a1b -->", Instant.EPOCH),
                    new RunRecord.Note(
                        2, 102, "actor", "a <!-- jagt-review:0a1b -->", Instant.EPOCH)))
            .build();

    assertThat(new NoNoteTwice().check(run)).isEmpty();
  }
}
