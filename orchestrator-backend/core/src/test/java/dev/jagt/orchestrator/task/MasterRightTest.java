package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MasterRightTest {

  @ParameterizedTest
  @CsvSource({"deploy,DEPLOY", "' Reply ',REPLY", "PLAN,PLAN", "repo,REPO"})
  void readsMasterRightByItsWord(String word, MasterRight right) {
    assertThat(MasterRight.of(word)).contains(right);
  }

  @Test
  void refusesMasterRightNoneOfTheWordsMean() {
    assertThat(MasterRight.of("sometimes")).isEmpty();
    assertThat(MasterRight.of(null)).isEmpty();
    assertThat(MasterRight.ids())
        .containsExactly("ship", "deploy", "revert", "reply", "answer", "plan", "repo", "lift");
  }
}
