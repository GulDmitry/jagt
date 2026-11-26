package dev.jagt.orchestrator.task.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UntakenTest {

  private final Untaken untaken = new Untaken("a1b2c3d4e5f60718", List.of("12"), "brief");

  @Test
  void isNamedByTheReportCarryingItsTag() {
    assertThat(untaken.namedBy("fixed, round a1b2c3d4")).isTrue();
  }

  @Test
  void isNamedByTheReportCarryingOneOfItsNoteIds() {
    assertThat(untaken.namedBy("answered note 12")).isTrue();
  }

  @Test
  void isNotNamedByNumbersThatOnlyContainTheNoteId() {
    assertThat(untaken.namedBy("changed 3 files, 120 lines")).isFalse();
  }

  @Test
  void isNotNamedByNoReportMessage() {
    assertThat(untaken.namedBy(null)).isFalse();
  }
}
