package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class EvalQuorumTest {

  @Test
  void caseHoldsWhenOneRunInThreeFlakes() {
    Deque<Executable> answers =
        new ArrayDeque<>(
            List.of(
                () -> {
                  throw new AssertionError("flaked once");
                },
                () -> {},
                () -> {}));

    assertThatCode(
            () ->
                new EvalQuorum(3, 2, null)
                    .hold("flaky", EvalSpend.UNMETERED, () -> answers.poll().execute()))
        .doesNotThrowAnyException();
  }

  @Test
  void caseFailsWithBothJudgesReasonsWhenTwoRunsInThreeFail() {
    Deque<Executable> answers =
        new ArrayDeque<>(
            List.of(
                () -> {
                  throw new AssertionError("judge said first");
                },
                () -> {},
                () -> {
                  throw new AssertionError("judge said second");
                }));

    assertThatThrownBy(
            () ->
                new EvalQuorum(3, 2, null)
                    .hold("broken", EvalSpend.UNMETERED, () -> answers.poll().execute()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("judge said first")
        .hasMessageContaining("judge said second");
  }

  @Test
  void thirdRunIsNeverMadeOnceTwoPassesInRowDecideTheCase() {
    AtomicInteger made = new AtomicInteger();

    new EvalQuorum(3, 2, null).hold("steady", EvalSpend.UNMETERED, made::incrementAndGet);

    assertThat(made).hasValue(2);
  }

  @Test
  void stalledRunIsCountedAsStalledAndWrongAnswerIsNot(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("eval-stats.jsonl");
    Deque<Executable> answers =
        new ArrayDeque<>(
            List.of(
                () -> {
                  throw EvalStalled.because("no answer");
                },
                () -> {
                  throw new AssertionError("wrong answer");
                },
                () -> {}));

    assertThatThrownBy(
            () ->
                new EvalQuorum(3, 2, file)
                    .hold("mixed", EvalSpend.UNMETERED, () -> answers.poll().execute()))
        .isInstanceOf(AssertionError.class);

    assertThat(Files.readAllLines(file))
        .singleElement()
        .asString()
        .contains("\"runs\":2")
        .contains("\"stalled\":1");
  }

  @Test
  void caseRecordsTheSpendBetweenItsFirstRunAndItsLast(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("eval-stats.jsonl");
    Deque<Double> meter = new ArrayDeque<>(List.of(1.0, 1.75));
    try {
      new EvalQuorum(1, 1, file).hold("metered", () -> OptionalDouble.of(meter.poll()), () -> {});
    } finally {
      System.clearProperty("eval.stats");
    }

    assertThat(Files.readAllLines(file)).singleElement().asString().contains("\"costUsd\":0.75");
  }

  @Test
  void morePassesThanRunsIsRefused() {
    assertThatThrownBy(() -> new EvalQuorum(2, 3, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3")
        .hasMessageContaining("2");
  }
}
