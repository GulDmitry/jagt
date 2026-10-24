package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CaseBookTest {

    @Test
    void readsACaseAsItsStepStatusesAndWhoSetsItOff() {
        CaseBook book = CaseBook.parse("session-cases.md",
                "# Cases\n\n## plan-by-human\n\nstep: plan\nat: PLAN_PENDING\nby: human\nYou approved the plan.\n");

        assertThat(book.cases()).containsExactly(new CaseBook.Case("session-cases.md", "plan-by-human", "plan",
                Set.of(TaskStatus.PLAN_PENDING), CaseBook.By.HUMAN));
    }

    @Test
    void readsAnyButAsEveryStatusOutsideTheList() {
        CaseBook book = CaseBook.parse("session-cases.md",
                "## wake\n\nstep: wake\nat: any but DONE\nby: jagt\nA limit reset.\n");

        assertThat(book.sends("wake", TaskStatus.DEPLOYED)).isTrue();
        assertThat(book.sends("wake", TaskStatus.DONE)).isFalse();
    }

    @Test
    void refusesACaseWrittenTwice() {
        CaseBook book = CaseBook.parse("session-cases.md", "## wake\n\nstep: wake\nat: any\nby: jagt\nA reset.\n\n"
                + "## wake\n\nstep: wake\nat: any\nby: jagt\nA reset.\n");

        assertThat(book.faults()).singleElement()
                .satisfies(fault -> assertThat(fault.text()).isEqualTo("session-cases.md: case wake is written twice"));
    }

    @Test
    void takesTheCopysCaseOverTheShippedOneOfTheSameId() {
        CaseBook shipped = CaseBook.parse("session-cases.md.dist", "## wake\n\nstep: wake\nat: any\nby: jagt\nA reset.\n");
        CaseBook copy = CaseBook.parse("session-cases.md", "## wake\n\nstep: wake\nat: IN_PROGRESS\nby: jagt\nA reset.\n");

        CaseBook book = copy.over(shipped);

        assertThat(book.sends("wake", TaskStatus.REVIEW_PENDING)).isFalse();
    }
}
