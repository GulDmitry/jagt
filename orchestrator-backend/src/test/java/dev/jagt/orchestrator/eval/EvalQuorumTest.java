package dev.jagt.orchestrator.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalQuorumTest {

    @Test
    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
    void aCaseHoldsWhenOneRunInThreeFlakes() {
        Deque<Executable> answers = new ArrayDeque<>(List.of(
                () -> {
                    throw new AssertionError("flaked once");
                },
                () -> { },
                () -> { }));

        assertThatCode(() -> new EvalQuorum(3, 2).hold("flaky", EvalSpend.UNMETERED, () -> answers.poll().execute()))
                .doesNotThrowAnyException();
    }

    @Test
    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
    void aCaseFailsWithBothJudgesReasonsWhenTwoRunsInThreeFail() {
        Deque<Executable> answers = new ArrayDeque<>(List.of(
                () -> {
                    throw new AssertionError("judge said first");
                },
                () -> { },
                () -> {
                    throw new AssertionError("judge said second");
                }));

        assertThatThrownBy(() -> new EvalQuorum(3, 2).hold("broken", EvalSpend.UNMETERED, () -> answers.poll().execute()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("judge said first")
                .hasMessageContaining("judge said second");
    }

    @Test
    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
    void aThirdRunIsNeverMadeOnceTwoPassesInARowDecideTheCase() {
        AtomicInteger made = new AtomicInteger();

        new EvalQuorum(3, 2).hold("steady", EvalSpend.UNMETERED, made::incrementAndGet);

        assertThat(made).hasValue(2);
    }

    @Test
    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
    void aStalledRunIsCountedAsStalledAndAWrongAnswerIsNot(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("eval-stats.jsonl");
        System.setProperty("eval.stats", file.toString());
        Deque<Executable> answers = new ArrayDeque<>(List.of(
                () -> {
                    throw EvalStalled.because("no answer");
                },
                () -> {
                    throw new AssertionError("wrong answer");
                },
                () -> { }));
        try {
            assertThatThrownBy(() -> new EvalQuorum(3, 2).hold("mixed", EvalSpend.UNMETERED, () -> answers.poll().execute()))
                    .isInstanceOf(AssertionError.class);
        } finally {
            System.clearProperty("eval.stats");
        }

        assertThat(Files.readAllLines(file)).singleElement().asString().contains("\"runs\":2").contains("\"stalled\":1");
    }

    @Test
    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
    void aCaseRecordsTheSpendBetweenItsFirstRunAndItsLast(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("eval-stats.jsonl");
        System.setProperty("eval.stats", file.toString());
        Deque<Double> meter = new ArrayDeque<>(List.of(1.0, 1.75));
        try {
            new EvalQuorum(1, 1).hold("metered", () -> OptionalDouble.of(meter.poll()), () -> { });
        } finally {
            System.clearProperty("eval.stats");
        }

        assertThat(Files.readAllLines(file)).singleElement().asString().contains("\"costUsd\":0.75");
    }

    @Test
    void morePassesThanRunsIsRefused() {
        assertThatThrownBy(() -> new EvalQuorum(2, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("2");
    }
}
