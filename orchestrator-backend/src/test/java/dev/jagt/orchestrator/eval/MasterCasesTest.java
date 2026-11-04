package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasterCasesTest {

    @Test
    void everyShippedRoundExpectsEitherAVerdictOrAnAnswer() {
        var cases = MasterCases.all();

        assertThat(cases).hasSize(21)
                .allSatisfy(loaded -> assertThat(loaded.verdict() == null).isEqualTo(loaded.answers()));
    }

    @Test
    void aPlanRoundKeepsItsPlanAndVerdict() {
        MasterCase loaded = MasterCases.parse("""
                case: a plan
                status: plan-pending
                instructions: Do one thing.
                baseline:
                  src/A.java: |
                    class A {
                    }
                plan: |
                  1. Change A.
                expect:
                  verdict: not-ready
                  names: [A.java]
                """);

        assertThat(loaded.status()).isEqualTo(TaskStatus.PLAN_PENDING);
        assertThat(loaded.plan()).isEqualTo("1. Change A.\n");
        assertThat(loaded.change()).isEmpty();
        assertThat(loaded.verdict()).isEqualTo(MasterReview.Kind.NOT_READY);
        assertThat(loaded.answers()).isFalse();
    }

    @Test
    void aQuestionRoundKeepsWhatTheHumanSaidAndWhatTheAnswerMustRefuse() {
        MasterCase loaded = MasterCases.parse("""
                case: a question
                status: in-progress
                instructions: Do one thing.
                baseline: {src/A.java: "class A {}"}
                question:
                  asked: Skip the test?
                  human_said: [ship it]
                expect:
                  names: [test]
                  refused: [later]
                """);

        assertThat(loaded.answers()).isTrue();
        assertThat(loaded.answer().question()).isEqualTo("Skip the test?");
        assertThat(loaded.answer().said()).containsExactly("ship it");
        assertThat(loaded.answer().refused()).containsExactly("later");
        assertThat(loaded.verdict()).isNull();
    }
}
