package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CaseBookTest {

    @Test
    void readsACaseAsItsStepStatusesWhoSetsItOffAndTheRepliesItTakes() {
        CaseBook book = CaseBook.parse("session-cases.md", "# Cases\n\n## plan-by-human\n\nstep: plan\nat: PLAN_PENDING\n"
                + "by: human\nreplies: IN_PROGRESS, REVIEW_PENDING; plain, question\nYou approved the plan.\n");

        assertThat(book.cases()).containsExactly(new CaseBook.Case("session-cases.md", "plan-by-human", "plan",
                Set.of(TaskStatus.PLAN_PENDING), CaseBook.By.HUMAN, new CaseBook.Replies(
                Set.of(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING), Set.of(AgentReport.PLAIN, AgentReport.QUESTION))));
    }

    @Test
    void readsAnyButAsEveryStatusOutsideTheList() {
        CaseBook book = CaseBook.parse("session-cases.md",
                "## wake\n\nstep: wake\nat: any but DONE\nby: jagt\nreplies: any; plain\nA limit reset.\n");

        assertThat(book.sends("wake", TaskStatus.DEPLOYED)).isTrue();
        assertThat(book.sends("wake", TaskStatus.DONE)).isFalse();
    }

    @Test
    void refusesACaseWrittenTwice() {
        CaseBook book = CaseBook.parse("session-cases.md", "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n\n"
                + "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");

        assertThat(book.faults()).singleElement()
                .satisfies(fault -> assertThat(fault.text()).isEqualTo("session-cases.md: case wake is written twice"));
    }

    @Test
    void takesTheCopysCaseOverTheShippedOneOfTheSameId() {
        CaseBook shipped = CaseBook.parse("session-cases.md.dist", "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");
        CaseBook copy = CaseBook.parse("session-cases.md", "## wake\n\nstep: wake\nat: IN_PROGRESS\nby: jagt\nreplies: any; plain\nA reset.\n");

        CaseBook book = copy.over(shipped);

        assertThat(book.sends("wake", TaskStatus.REVIEW_PENDING)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''",
            "IN_PROGRESS",
            "IN_PROGRESS; maybe",
            "WAITING; plain"
    })
    void refusesACaseWhoseRepliesAreMissingOrNameNoStatusOrKind(String replies) {
        CaseBook book = CaseBook.parse("session-cases.md",
                "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: " + replies + "\nA reset.\n");

        assertThat(book.faults()).singleElement().satisfies(fault -> assertThat(fault.text())
                .startsWith("session-cases.md: case wake has replies: '" + replies + "'"));
    }

    @Test
    void takesForAJoinedStepWhatAnyCaseOfItsStepsTakes() {
        CaseBook book = CaseBook.parse("session-cases.md",
                "## round-threads\n\nstep: round-threads\nat: any\nby: jagt\nreplies: IN_PROGRESS; plain\nThreads.\n\n"
                        + "## round-threads-done\n\nstep: round-threads-done\nat: any\nby: jagt\n"
                        + "replies: REVIEW_PENDING; no_changes\nThe round ends.\n");

        CaseBook.Replies replies = book.replies("round-threads+round-threads-done").orElseThrow();

        assertThat(replies.takes(TaskStatus.REVIEW_PENDING, AgentReport.NO_CHANGES)).isTrue();
    }
}
