package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeployRedLineTest {

  @Test
  void marksALineThatStartsWithTheCauseAndNoOther() {
    assertThat(DeployRedLine.marks(DeployRedLine.CAUSE + ": unit-tests")).isTrue();
    assertThat(DeployRedLine.marks("merge conflict")).isFalse();
    assertThat(DeployRedLine.marks(null)).isFalse();
  }
}
