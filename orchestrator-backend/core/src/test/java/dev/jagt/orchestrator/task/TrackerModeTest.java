package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TrackerModeTest {

  @ParameterizedTest
  @CsvSource({"OFF,false,false", "TAKE,true,false", "CLOSE,false,true", "BOTH,true,true"})
  void drivesTheEndsOfTheLoopTheTrackerModeNames(TrackerMode mode, boolean takes, boolean closes) {
    assertThat(mode.takes()).isEqualTo(takes);
    assertThat(mode.closes()).isEqualTo(closes);
  }

  @ParameterizedTest
  @CsvSource({"take,TAKE", "' Both ',BOTH", "CLOSE,CLOSE"})
  void readsTrackerModeByItsWord(String word, TrackerMode mode) {
    assertThat(TrackerMode.of(word)).contains(mode);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void leavesTheTrackerOffWhenNoModeWasWritten(String word) {
    assertThat(TrackerMode.of(word)).contains(TrackerMode.OFF);
  }

  @Test
  void refusesTrackerModeNoneOfTheWordsMean() {
    assertThat(TrackerMode.of("sometimes")).isEmpty();
    assertThat(TrackerMode.ids()).containsExactly("off", "take", "close", "both");
  }
}
