package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.mutation.MutationRuns;
import org.junit.jupiter.api.Test;

class StandRunsSelectionTest {

  @Test
  void everyScenarioIsChosenWhenNoneIsNamed() {
    assertThat(StandRuns.of("happy-path")).isNotEmpty();
  }

  @Test
  void mutationRunIsPickedFromTheShippedBookWhenNoneIsNamed() {
    assertThat(MutationRuns.of("mutation")).hasSize(StandMode.selected("").size());
  }
}
