package dev.jagt.orchestrator.task.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeployRedLineTest {

  @Test
  void marksLineThatStartsWithTheCauseAndNoOther() {
    assertThat(DeployRedLine.marks(DeployRedLine.CAUSE + ": unit-tests")).isTrue();
    assertThat(DeployRedLine.marks("merge conflict")).isFalse();
    assertThat(DeployRedLine.marks(null)).isFalse();
  }
}
