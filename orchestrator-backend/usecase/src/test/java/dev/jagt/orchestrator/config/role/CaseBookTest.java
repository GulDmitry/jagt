package dev.jagt.orchestrator.config.role;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CaseBookTest {

  @Test
  void readsCaseAsItsStepStatusesWhoSetsItOffAndTheRepliesItTakes() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            # Cases

            ## plan-by-human

            step: plan
            at: PLAN_PENDING
            by: human
            replies: IN_PROGRESS, REVIEW_PENDING; plain, question
            You approved the plan.
            """);

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
  void readsAcceptanceAsWhoSetsStepOff() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            ## acceptance-round-by-acceptance

            step: acceptance-round
            at: DEPLOYED
            by: acceptance
            replies: IN_PROGRESS; plain, wait
            Acceptance sends its rows back.
            """);

    assertThat(book.faults()).isEmpty();
    assertThat(
            book.sends("acceptance-round", TaskStatus.DEPLOYED, new CaseBook.By.Role("acceptance")))
        .isTrue();
    assertThat(
            book.replies("acceptance-round")
                .orElseThrow()
                .takes(TaskStatus.IN_PROGRESS, AgentReport.WAIT))
        .isTrue();
  }

  @Test
  void refusesByNamingNoPartyAndNoInstalledChecker() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            ## acceptance-round-by-lint

            step: acceptance-round
            at: DEPLOYED
            by: lint
            replies: IN_PROGRESS; plain
            Nobody installed.
            """);

    assertThat(book.problems(Set.of("acceptance-round"), "roles/session/cases.md", List.of()))
        .hasSize(1);
  }

  @Test
  void loadsNoCaseOfShippedCheckerNotInstalledNorRequiresItsStep() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            ## acceptance-round-by-acceptance

            step: acceptance-round
            at: DEPLOYED
            by: acceptance
            replies: IN_PROGRESS; plain
            Acceptance sends its rows back.
            """);

    assertThat(
            book.problems(
                Set.of("acceptance-round"),
                "roles/session/cases.md",
                List.of(),
                Set.of("acceptance")))
        .isEmpty();
  }

  @Test
  void refusesByNamingNoShippedCheckerWhereOthersAreNotInstalled() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            ## acceptance-round-by-acceptnce

            step: acceptance-round
            at: DEPLOYED
            by: acceptnce
            replies: IN_PROGRESS; plain
            A typo.
            """);

    assertThat(
            book.problems(
                Set.of("acceptance-round"),
                "roles/session/cases.md",
                List.of(),
                Set.of("acceptance")))
        .hasSize(1);
  }

  @Test
  void readsAnyButAsEveryStatusOutsideTheList() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            ## wake

            step: wake
            at: any but DONE
            by: jagt
            replies: any; plain
            A limit reset.
            """);

    assertThat(book.sends("wake", TaskStatus.DEPLOYED)).isTrue();
    assertThat(book.sends("wake", TaskStatus.DONE)).isFalse();
  }

  @Test
  void refusesCaseWrittenTwice() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            ## wake

            step: wake
            at: any
            by: jagt
            replies: any; plain
            A reset.

            ## wake

            step: wake
            at: any
            by: jagt
            replies: any; plain
            A reset.
            """);

    assertThat(book.faults())
        .singleElement()
        .satisfies(fault ->
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
  void refusesCaseWhoseRepliesAreMissingOrNameNoStatusOrKind(String replies) {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            "## wake\n\nstep: wake\nat: any\nby: jagt\nreplies: " + replies + "\nA reset.\n");

    assertThat(book.faults())
        .singleElement()
        .satisfies(fault ->
            assertThat(fault.text())
                .startsWith("roles/session/cases.md: case wake has replies: '" + replies + "'"));
  }

  @Test
  void takesForJoinedStepWhatAnyCaseOfItsStepsTakes() {
    CaseBook book =
        CaseBook.parse(
            "roles/session/cases.md",
            """
            ## round-threads

            step: round-threads
            at: any
            by: jagt
            replies: IN_PROGRESS; plain
            Threads.

            ## round-threads-done

            step: round-threads-done
            at: any
            by: jagt
            replies: REVIEW_PENDING; no_changes
            The round ends.
            """);

    CaseBook.Replies replies = book.replies("round-threads+round-threads-done").orElseThrow();

    assertThat(replies.takes(TaskStatus.REVIEW_PENDING, AgentReport.NO_CHANGES)).isTrue();
  }
}
