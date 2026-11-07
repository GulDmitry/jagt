package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CaseBookTest {

  @Test
  void readsACaseAsItsStepStatusesWhoSetsItOffAndTheRepliesItTakes() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "# Cases\n\n"
                + "## plan-by-human\n\n"
                + "step: plan\n"
                + "at: PLAN_PENDING\n"
                + "by: human\n"
                + "replies: IN_PROGRESS, REVIEW_PENDING; plain, question\n"
                + "You approved the plan.\n");

    assertThat(book.cases())
        .containsExactly(
            new CaseBook.Case(
                "roles/session/cases.md",
                "plan-by-human",
                "plan",
                Set.of(TaskStatus.PLAN_PENDING),
                CaseBook.By.HUMAN,
                new CaseBook.Replies(
                    Set.of(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING),
                    Set.of(AgentReport.PLAIN, AgentReport.QUESTION))));
  }

  @Test
  void readsQaAsWhoSetsAStepOff() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "## qa-round-by-qa\n\nstep: qa-round\nat: DEPLOYED\n"
                + "by: qa\nreplies: IN_PROGRESS; plain, wait\nQA sends its rows back.\n");

    assertThat(book.faults()).isEmpty();
    assertThat(book.sends("qa-round", TaskStatus.DEPLOYED, new CaseBook.By.Role("qa"))).isTrue();
    assertThat(
            book.replies("qa-round").orElseThrow().takes(TaskStatus.IN_PROGRESS, AgentReport.WAIT))
        .isTrue();
  }

  @Test
  void refusesAByNamingNoPartyAndNoInstalledChecker() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "## qa-round-by-lint\n\nstep: qa-round\nat: DEPLOYED\n"
                + "by: lint\nreplies: IN_PROGRESS; plain\nNobody installed.\n");

    assertThat(book.cases()).isEmpty();
    assertThat(book.faults()).hasSize(1);
  }

  @Test
  void readsAnyButAsEveryStatusOutsideTheList() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "## wake\n\n"
                + "step: wake\n"
                + "at: any but DONE\n"
                + "by: jagt\n"
                + "replies: any; plain\n"
                + "A limit reset.\n");

    assertThat(book.sends("wake", TaskStatus.DEPLOYED)).isTrue();
    assertThat(book.sends("wake", TaskStatus.DONE)).isFalse();
  }

  @Test
  void refusesACaseWrittenTwice() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n\n"
                + "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");

    assertThat(book.faults())
        .singleElement()
        .satisfies(
            fault ->
                assertThat(fault.text())
                    .isEqualTo("roles/session/cases.md: case wake is written twice"));
  }

  @Test
  void takesTheCopysCaseOverTheShippedOneOfTheSameId() {
    CaseBook shipped =
        CaseBook.parse(
            "roles/session/cases.md.dist",
            "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: any; plain\nA reset.\n");
    CaseBook copy =
        CaseBook.parse(
            "roles/session/cases.md",
            "## wake\n\nstep: wake\nat: IN_PROGRESS\nby: jagt\nreplies: any; plain\nA reset.\n");

    CaseBook book = copy.over(shipped);

    assertThat(book.sends("wake", TaskStatus.REVIEW_PENDING)).isFalse();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"''", "IN_PROGRESS", "IN_PROGRESS; maybe", "WAITING; plain"})
  void refusesACaseWhoseRepliesAreMissingOrNameNoStatusOrKind(String replies) {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: " + replies + "\nA reset.\n");

    assertThat(book.faults())
        .singleElement()
        .satisfies(
            fault ->
                assertThat(fault.text())
                    .startsWith(
                        "roles/session/cases.md: case wake has replies: '" + replies + "'"));
  }

  @Test
  void takesForAJoinedStepWhatAnyCaseOfItsStepsTakes() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "## round-threads\n\n"
                + "step: round-threads\n"
                + "at: any\n"
                + "by: jagt\n"
                + "replies: IN_PROGRESS; plain\n"
                + "Threads.\n\n"
                + "## round-threads-done\n\n"
                + "step: round-threads-done\n"
                + "at: any\n"
                + "by: jagt\n"
                + "replies: REVIEW_PENDING; no_changes\n"
                + "The round ends.\n");

    CaseBook.Replies replies = book.replies("round-threads+round-threads-done").orElseThrow();

    assertThat(replies.takes(TaskStatus.REVIEW_PENDING, AgentReport.NO_CHANGES)).isTrue();
  }
}
