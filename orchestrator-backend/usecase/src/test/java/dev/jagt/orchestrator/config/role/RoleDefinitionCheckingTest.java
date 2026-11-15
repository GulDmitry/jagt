package dev.jagt.orchestrator.config.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoleDefinitionCheckingTest {

  @Test
  void threeReadStepsAreCheckAgainAndDecline() {
    RoleDefinition.Checking checking = new RoleDefinition.Checking(true, List.of("a", "b", "c"));

    assertThat(List.of(checking.checkStep(), checking.againStep(), checking.declineStep()))
        .containsExactly("a", "b", "c");
  }

  @Test
  void oneReadStepIsRepeatedForTheRunAgain() {
    RoleDefinition.Checking checking = new RoleDefinition.Checking(true, List.of("a"));

    assertThat(checking.againStep()).isEqualTo("a");
  }
}
