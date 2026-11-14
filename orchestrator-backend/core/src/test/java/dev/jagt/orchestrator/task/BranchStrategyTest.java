package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BranchStrategyTest {

  @ParameterizedTest
  @CsvSource({"recreate,RECREATE", "' Resume ',RESUME", "FRESH,FRESH"})
  void readsBranchStrategyByItsWord(String word, BranchStrategy strategy) {
    assertThat(BranchStrategy.of(word)).isEqualTo(strategy);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void startsFreshWhenNoBranchStrategyWasWritten(String word) {
    assertThat(BranchStrategy.of(word)).isEqualTo(BranchStrategy.FRESH);
  }

  @Test
  void refusesBranchStrategyNoneOfTheWordsMeanAndListsTheAllowedOnes() {
    assertThatThrownBy(() -> BranchStrategy.of("sometimes"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown branchStrategy 'sometimes'. Allowed: fresh, recreate, resume");
  }

  @Test
  void offersEveryBranchStrategyWithTheHintItIsDescribedBy() {
    assertThat(BranchStrategy.choices())
        .containsExactly(
            new BranchStrategy.Choice("fresh", "stop if the branch exists"),
            new BranchStrategy.Choice("recreate", "delete the branch, start over from the base"),
            new BranchStrategy.Choice("resume", "continue the branch and its commits"));
  }
}
