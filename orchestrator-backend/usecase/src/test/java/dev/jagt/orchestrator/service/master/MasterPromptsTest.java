package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.Probe;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasterPromptsTest {

  private final MasterPanel.RoundRead emptyRead =
      new MasterPanel.RoundRead("", "", "", List.of(), "");
  private final MasterPanel.Briefs briefsPair =
      new MasterPanel.Briefs("judge hard", "never commit unasked");
  private final TaskState reviewPending =
      TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING).build();
  private final TaskState planPending =
      TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING).build();
  private final TaskState inProgress =
      TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();

  private static final List<MasterPanel.Role> TWO =
      List.of(
          new MasterPanel.Role("chaplain", "should it exist", "", List.of(), ""),
          new MasterPanel.Role("developer", "is the code right", "", List.of(), ""));

  @Test
  void showsEachReviewerTheBriefTheAuthorWorkedTo() {
    String shared = MasterPrompts.shared("judge hard", "never commit unasked", false, true);

    assertThat(shared).contains("never commit unasked");
  }

  @Test
  void tellsEachReviewerTheFlagsItsGitReadsMustCarry() {
    String shared = MasterPrompts.shared("judge hard", "never commit unasked", false, true);

    assertThat(shared).contains("`--no-ext-diff --no-textconv`");
  }

  @Test
  void tellsEachReviewerHowToReadWorktreePastTheFirst() {
    String shared = MasterPrompts.shared("judge hard", "never commit unasked", false, true);

    assertThat(shared).contains("`git -C <worktree>`");
  }

  @Test
  void letsReviewerStandingInForTheHumanDecideWhatItWouldHaveAsked() {
    String shared = MasterPrompts.shared("judge hard", "never commit unasked", true, true);

    assertThat(shared).contains("never answer question");
  }

  @Test
  void quotesTheTicketReadForTheRoundToEachReviewer() {

    String prompt =
        MasterPrompts.quote(
            "ABC-1",
            reviewPending,
            new MasterPanel.RoundRead("Summary: accept v3 calls", "", "", List.of(), ""));

    assertThat(prompt)
        .contains("<outside source=\"ticket\">\nSummary: accept v3 calls\n</outside>");
  }

  @Test
  void quotesTheProbesJagtRanToEachReviewerAsFacts() {
    TaskState task =
        TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING)
            .probes(
                List.of(
                    Probe.specified(Probe.Kind.COMMAND, List.of("./gradlew", "test"), 0)
                        .probed(true, "exit 0")))
            .build();

    String prompt = MasterPrompts.quote("ABC-1", task, emptyRead);

    assertThat(prompt).contains("<probes>\ncommand ./gradlew test — passed\nexit 0\n</probes>");
  }

  @Test
  void showsThePlanReaderTheProbesJagtWillRun() {
    TaskState task =
        TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING)
            .probes(List.of(Probe.specified(Probe.Kind.REF, List.of("refs/tags/v1"), 0)))
            .build();

    String prompt =
        MasterPrompts.planPrompt("ABC-1", task, "judge hard", "1. tag v1", emptyRead, false, true);

    assertThat(prompt).contains("<probes>\nref refs/tags/v1\n</probes>");
  }

  @Test
  void judgesThePlanAgainstTheTicketItQuotes() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1",
            planPending,
            "judge hard",
            "1. add the v3 route",
            new MasterPanel.RoundRead("Summary: accept v3 calls", "", "", List.of(), ""),
            false,
            true);

    assertThat(prompt)
        .contains(
            "<outside source=\"ticket\">\nSummary: accept v3 calls\n</outside>",
            "<plan>\n1. add the v3 route\n</plan>",
            "whether this plan does what the ticket asks")
        .doesNotContain("<session_notes>", "wrote no plan.md", "never answer question");
  }

  @Test
  void tellsPlanReaderLoadingNoServerThatTheTicketWasNeverRead() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, false);

    assertThat(prompt).doesNotContain("tracker tools").contains("The ticket was not read");
  }

  @Test
  void sendsPlanReaderLoadingServerToItsMcpToolsWhicheverServerThatIs() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, true);

    assertThat(prompt)
        .contains("Read the ticket with your MCP tools")
        .doesNotContain("tracker tools");
  }

  @Test
  void tellsPlanReaderHowToWriteItsGitReads() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, true);

    assertThat(prompt).contains("`--no-ext-diff --no-textconv`", "`git -C <worktree>`");
  }

  @Test
  void tellsTheMasterAnsweringQuestionHowToWriteItsGitReads() {

    String prompt =
        MasterPrompts.answerPrompt(
            "ABC-1",
            inProgress,
            briefsPair,
            new MasterPanel.Asked("outcome=question — keep v2?", "", List.of(), false),
            true);

    assertThat(prompt).contains("`--no-ext-diff --no-textconv`", "`git -C <worktree>`");
  }

  @Test
  void tellsTheMasterAnsweringQuestionWithNoServerThatTheTicketWasNeverRead() {

    String prompt =
        MasterPrompts.answerPrompt(
            "ABC-1",
            inProgress,
            briefsPair,
            new MasterPanel.Asked("outcome=question — keep v2?", "", List.of(), false),
            false);

    assertThat(prompt).contains("The ticket was not read").doesNotContain("Read the ticket");
  }

  @Test
  void quotesTheDiffReadForTheRoundToEachReviewer() {

    String prompt =
        MasterPrompts.quote(
            "ABC-1", reviewPending, new MasterPanel.RoundRead("", "+int x;\n", "", List.of(), ""));

    assertThat(prompt).contains("<diff>\n+int x;\n</diff>");
  }

  @Test
  void asksTheMasterToDecideTheSessionsQuestionRatherThanDeferIt() {

    String prompt =
        MasterPrompts.answerPrompt(
            "ABC-1",
            inProgress,
            briefsPair,
            new MasterPanel.Asked("outcome=question — keep v2?", "", List.of(), false),
            false);

    assertThat(prompt).contains("outcome=question — keep v2?").contains("Never answer question");
  }

  @Test
  void asksTheMasterForTheChangeThatTurnsRedCheckGreenRatherThanAnOverride() {

    String prompt =
        MasterPrompts.answerPrompt(
            "ABC-1",
            reviewPending,
            briefsPair,
            new MasterPanel.Asked(
                "outcome=question — refactor, or override the gate?", "", List.of(), false),
            false);

    assertThat(prompt)
        .contains(
            "an override, an exception or a human's action is no answer while a change in"
                + " the worktrees can pass it");
  }

  @Test
  void asksTheMasterToTakeTheBestOptionOnceItsAnswersChangedNothing() {

    String prompt =
        MasterPrompts.answerPrompt(
            "ABC-1",
            reviewPending,
            briefsPair,
            new MasterPanel.Asked("outcome=question — override the gate?", "", List.of(), true),
            false);

    assertThat(prompt).contains("where options remain, recommend the best and take it");
  }

  @Test
  void opensEveryMasterReadOnTheTaskFinishedGreenRatherThanOnJudging() {

    String answer =
        MasterPrompts.answerPrompt(
            "ABC-1",
            reviewPending,
            briefsPair,
            new MasterPanel.Asked("outcome=question — keep v2?", "", List.of(), false),
            false);
    String review = MasterPrompts.shared("judge hard", "never commit unasked", true, true);

    assertThat(answer)
        .startsWith(
            "<goal>\nYour goal is the task finished: ready to merge, its request's checks green");
    assertThat(review)
        .startsWith(
            "<goal>\nYour goal is the task finished: ready to merge, its request's checks green");
  }

  @Test
  void definesAllFiveSeveritiesToThePlanReaderAsToEveryRoundReader() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, true);

    assertThat(prompt)
        .contains(
            "- blocking: someone relying on it today breaks.",
            "- wrong: not what the ticket asks.",
            "- unguarded: right, but nothing fails when it breaks.",
            "- unproven: you would decide it",
            "- noise: style or taste.")
        .doesNotContain("Only noise lets the plan pass");
  }

  @Test
  void rulesWordingGlossaryAndStyleNeverStopTheRound() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, true);

    assertThat(prompt)
        .contains(
            "only for correctness or a stated requirement",
            "wording, glossary and style are noise");
  }

  @Test
  void tagsTheRoleAndForbidsPreambleInEveryMasterPrompt() {
    MasterPanel.RoundRead read = emptyRead;

    assertThat(MasterPrompts.prompt(TWO.getFirst()))
        .startsWith("<role>You are the chaplain")
        .endsWith("</role>\nRespond directly, no preamble.");
    assertThat(MasterPrompts.planPrompt("ABC-1", planPending, "judge hard", "", read, false, true))
        .contains("<role>You judge the plan of task ABC-1")
        .endsWith("Respond directly, no preamble.");
    assertThat(
            MasterPrompts.answerPrompt(
                "ABC-1",
                planPending,
                new MasterPanel.Briefs("judge hard", "author"),
                new MasterPanel.Asked("keep v2?", "", List.of(), false),
                true))
        .contains("<role>You stand in for the human on task ABC-1.</role>")
        .endsWith("Respond directly, no preamble.");
  }

  @Test
  void holdsEveryReviewerToWhatEarlierRoundsSettled() {

    String prompt =
        MasterPrompts.quote(
            "ABC-1",
            reviewPending,
            new MasterPanel.RoundRead(
                "", "", "- keep the extraction per the human", List.of(), ""));

    assertThat(prompt).contains("binding").contains("- keep the extraction per the human");
  }

  @Test
  void quotesWhatTheHumanTypedToTheSessionToEachReviewer() {

    String prompt =
        MasterPrompts.quote(
            "ABC-1",
            reviewPending,
            new MasterPanel.RoundRead(
                "", "", "- keep the old field name", List.of("rename it everywhere"), ""));

    assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
  }

  @Test
  void quotesTheSessionsNotesSoReviewerCanCheckWhatItDisputed() {

    String prompt =
        MasterPrompts.quote(
            "ABC-1",
            reviewPending,
            new MasterPanel.RoundRead(
                "", "", "", List.of(), "disputed: drop v2 — Api.kt:12 has no caller"));

    assertThat(prompt)
        .contains("<session_notes>\ndisputed: drop v2 — Api.kt:12 has no caller\n</session_notes>");
  }

  @Test
  void quotesWhatTheHumanTypedToTheSessionWhenAnsweringInTheirStead() {

    String prompt =
        MasterPrompts.answerPrompt(
            "ABC-1",
            inProgress,
            briefsPair,
            new MasterPanel.Asked(
                "outcome=question — rename the field?", "", List.of("rename it everywhere"), false),
            false);

    assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
  }

  @Test
  void quotesTheSessionsNotesToThePlanReader() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1",
            planPending,
            "judge hard",
            "1. add the v3 route",
            new MasterPanel.RoundRead("", "", "", List.of(), "disputed: the ticket row 2"),
            false,
            true);

    assertThat(prompt).contains("<session_notes>\ndisputed: the ticket row 2\n</session_notes>");
  }

  @Test
  void holdsPlanReaderToTheFactThatTheSessionWroteNoPlan() {

    String prompt =
        MasterPrompts.planPrompt("ABC-1", planPending, "judge hard", "", emptyRead, false, true);

    assertThat(prompt)
        .contains("The session wrote no plan.md: that alone is not ready.")
        .doesNotContain("<plan>");
  }

  @Test
  void letsPlanReaderStandingInForTheHumanDecideWhatTheTicketLeavesOpen() {

    String prompt =
        MasterPrompts.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, true, true);

    assertThat(prompt).contains("never answer question");
  }

  @Test
  void namesTheTaskItsWorktreesAndItsTicketToTheMasterAnsweringQuestion() {
    TaskState task =
        TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
            .ticketUrl("https://tracker.example/ABC-1")
            .build();

    String prompt =
        MasterPrompts.answerPrompt(
            "ABC-1",
            task,
            briefsPair,
            new MasterPanel.Asked("outcome=question — keep v2?", "", List.of(), false),
            true);

    assertThat(prompt)
        .contains(
            "The task: ABC-1, worktrees [/wt/ABC-1-proj]", "ticket https://tracker.example/ABC-1.");
  }

  @Test
  void namesTheRoundsTicketToEachReviewerWhereTheTaskHasOne() {
    TaskState task =
        TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.REVIEW_PENDING)
            .ticketUrl("https://tracker.example/ABC-1")
            .build();

    String prompt = MasterPrompts.quote("ABC-1", task, emptyRead);

    assertThat(prompt)
        .contains("ticket https://tracker.example/ABC-1.")
        .doesNotContain("ticket none");
  }
}
