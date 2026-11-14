package dev.jagt.orchestrator.task.master;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MasterModeTest {

  @ParameterizedTest
  @CsvSource({"act,ACT", "' Judge ',JUDGE", "OFF,OFF"})
  void readsMasterModeByItsWord(String word, MasterMode mode) {
    assertThat(MasterMode.of(word)).contains(mode);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void judgesWhenNoMasterModeWasWritten(String word) {
    assertThat(MasterMode.of(word)).contains(MasterMode.JUDGE);
  }

  @Test
  void refusesMasterModeNoneOfTheWordsMean() {
    assertThat(MasterMode.of("sometimes")).isEmpty();
    assertThat(MasterMode.ids()).containsExactly("off", "judge", "act");
  }
}
