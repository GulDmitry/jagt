package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCriteriaTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "- [here] the list shows the status",
            "- [here, own] the list shows the status",
            "- [elsewhere: frontend] the page shows the status",
            "- [elsewhere: frontend, own] the page shows the status",
            "- [wrong: the ticket names a field the API lacks] the page shows the status",
            "- [wrong: contradicts the ticket's second item, own] the page shows the status",
    })
    void acceptsACriterionCarryingATag(String item) {
        List<Violation> violations = PlanCriteria.violationsIn("# Plan\n\n## Acceptance criteria\n" + item + "\n");

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "- the list shows the status",
            "- [] the list shows the status",
            "- [here]",
            "- [there] the list shows the status",
            "- [elsewhere] the page shows the status",
            "- [elsewhere:] the page shows the status",
            "- [wrong] the page shows the status",
            "- [wrong: ] the page shows the status",
            "- [here, mine] the list shows the status",
    })
    void refusesACriterionWithoutAValidTag(String item) {
        List<Violation> violations = PlanCriteria.violationsIn("## Acceptance criteria\n" + item + "\n");

        assertThat(violations).singleElement().asString().contains(item);
    }

    @Test
    void refusesAPlanWithoutTheSection() {
        List<Violation> violations = PlanCriteria.violationsIn("# Plan\n- [here] a criterion outside the section\n");

        assertThat(violations).singleElement().asString().contains("## Acceptance criteria");
    }

    @Test
    void refusesASectionHoldingNoItem() {
        List<Violation> violations = PlanCriteria.violationsIn("## Acceptance criteria\n\nTBD\n\n## Files\n- [here] x\n");

        assertThat(violations).singleElement().asString().contains("at least one criterion");
    }

    @Test
    void readsOnlyTheItemsBeforeTheNextHeading() {
        List<Violation> violations = PlanCriteria.violationsIn(
                "## Acceptance criteria\n- [here] one\n- [elsewhere: backend] two\n\n## Files\n- src/Foo.java\n");

        assertThat(violations).isEmpty();
    }
}
