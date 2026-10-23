package dev.jagt.orchestrator.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalQuorumTest {

    @Test
    void aCaseHoldsWhenOneRunInThreeFlakes() {
        Deque<Executable> answers = new ArrayDeque<>(List.of(
                () -> {
                    throw new AssertionError("flaked once");
                },
                () -> { },
                () -> { }));

        assertThatCode(() -> new EvalQuorum(3, 2).hold("flaky", () -> answers.poll().execute()))
                .doesNotThrowAnyException();
    }

    @Test
    void aCaseFailsWithBothJudgesReasonsWhenTwoRunsInThreeFail() {
        Deque<Executable> answers = new ArrayDeque<>(List.of(
                () -> {
                    throw new AssertionError("judge said first");
                },
                () -> { },
                () -> {
                    throw new AssertionError("judge said second");
                }));

        assertThatThrownBy(() -> new EvalQuorum(3, 2).hold("broken", () -> answers.poll().execute()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("judge said first")
                .hasMessageContaining("judge said second");
    }

    @Test
    void aThirdRunIsNeverMadeOnceTwoPassesInARowDecideTheCase() {
        AtomicInteger made = new AtomicInteger();

        new EvalQuorum(3, 2).hold("steady", made::incrementAndGet);

        assertThat(made).hasValue(2);
    }

    @Test
    void morePassesThanRunsIsRefused() {
        assertThatThrownBy(() -> new EvalQuorum(2, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("2");
    }
}
