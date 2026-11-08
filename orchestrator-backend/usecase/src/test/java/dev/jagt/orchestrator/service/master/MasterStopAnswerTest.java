package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MasterStopAnswerTest {

  @Test
  void liftsWhereTheFirstLineStartsWithLiftAndItsReason() {
    MasterStopAnswer answer =
        MasterStopAnswer.of("lift: each round answered a new report\nthe rest is read by nobody");

    assertThat(answer)
        .isEqualTo(
            new MasterStopAnswer(MasterStopAnswer.Kind.LIFT, "each round answered a new report"));
  }

  @Test
  void returnsTheTaskWhereTheFirstLineStartsWithReturnAndAnInstruction() {
    MasterStopAnswer answer = MasterStopAnswer.of("Return: fix the fixture, not the test");

    assertThat(answer)
        .isEqualTo(
            new MasterStopAnswer(MasterStopAnswer.Kind.RETURN, "fix the fixture, not the test"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "lift",
        "lift:",
        "lift:   ",
        "I would lift it",
        "maybe: lift",
        "human: the tree is yours to judge"
      })
  void leavesTheStopToTheHumanWhereTheAnswerIsNoLiftOrReturnWithWords(String said) {
    assertThat(MasterStopAnswer.of(said).kind()).isEqualTo(MasterStopAnswer.Kind.HUMAN);
  }

  @Test
  void escalatesWhereTheFirstLineStartsWithEscalateAndItsReason() {
    MasterStopAnswer answer = MasterStopAnswer.of("Escalate: sonnet looped on the same fixture");

    assertThat(answer)
        .isEqualTo(
            new MasterStopAnswer(
                MasterStopAnswer.Kind.ESCALATE, "sonnet looped on the same fixture"));
  }
}
