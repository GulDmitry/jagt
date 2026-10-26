package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchJudgementsTest {

    @ParameterizedTest
    @ValueSource(strings = {"yes — a screen changes", "no — config-only change", "No: nothing to draw", "yes - two screens"})
    void acceptsAYesOrNoCarryingItsWhy(String answer) {
        assertThat(SwitchJudgements.violations(Map.of("visualize", answer), List.of("visualize"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "no", "no —", "no — ", "maybe — unsure", "worth a picture"})
    void refusesAnAnswerWithoutAWhyOrAVerdict(String answer) {
        assertThat(SwitchJudgements.violations(Map.of("visualize", answer), List.of("visualize")))
                .extracting(Violation::field).containsExactly("switches");
    }

    @Test
    void namesASwitchThatIsOnAndLeftUnanswered() {
        List<Violation> violations = SwitchJudgements.violations(Map.of("visualize", "no — one-line fix"),
                List.of("autoplan", "visualize"));

        assertThat(violations).singleElement().satisfies(violation -> assertThat(violation.expected())
                .contains("autoplan").contains("yes — <why>"));
    }

    @Test
    void refusesASwitchJagtDoesNotHave() {
        assertThat(SwitchJudgements.violations(Map.of("telepathy", "yes — why"), List.of())).singleElement()
                .satisfies(violation -> assertThat(violation.expected()).contains("telepathy"));
    }

    @Test
    void spellsAnAnswerOneWay() {
        assertThat(SwitchJudgements.normalised(Map.of("Visualize", " NO: config-only change ")))
                .containsExactly(Map.entry("visualize", "no — config-only change"));
    }
}
