package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class MasterPanelTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path stateRoot;
  @TempDir Path worktree;

  private final ChargedReviews reviews = mock(ChargedReviews.class);
  private final MasterBriefs briefs = mock(MasterBriefs.class);
  private final RoundQuotes quotes = mock(RoundQuotes.class);
  private final ConfigService.ConfigFile.MasterConfig judge =
      new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
  private final ConfigService.ConfigFile.MasterConfig act =
      new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
  private final MasterPanel.RoundRead emptyRead =
      new MasterPanel.RoundRead("", "", "", List.of(), "");
  private final MasterPanel.Briefs briefsPair =
      new MasterPanel.Briefs("judge hard", "never commit unasked");
  private final Judgement ready = new Judgement("", "ready", List.of(), "", List.of(), "");

  private StateService state;
  private MasterReview stored;
  private MasterPanel panel;
  private TaskState reviewPending;
  private TaskState planPending;
  private TaskState inProgress;

  @BeforeEach
  void wirePanel() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(stateRoot.toString())
                    .withStateFile(stateRoot.resolve("state.json").toString())),
            reducer,
            event -> {});
    stored = new MasterReview(state);
    panel = new MasterPanel(reviews, briefs, quotes, stored);
    reviewPending =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
    planPending = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
    inProgress = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
    when(briefs.author()).thenReturn("");
    when(quotes.round("ABC-1", reviewPending)).thenReturn(Optional.of(emptyRead));
    when(quotes.question(inProgress)).thenReturn(Optional.of(emptyRead));
    when(reviews.review(eq("ABC-1"), any())).thenReturn(ready);
  }

  private static final List<MasterPanel.Role> TWO =
      List.of(
          new MasterPanel.Role("chaplain", "should it exist", "", List.of()),
          new MasterPanel.Role("developer", "is the code right", "", List.of()));

  @Test
  void showsEachReviewerTheBriefTheAuthorWorkedTo() {
    String shared = MasterPanel.shared("judge hard", "never commit unasked", false, true);

    assertThat(shared).contains("never commit unasked");
  }

  @Test
  void tellsEachReviewerTheFlagsItsGitReadsMustCarry() {
    String shared = MasterPanel.shared("judge hard", "never commit unasked", false, true);

    assertThat(shared).contains("`--no-ext-diff --no-textconv`");
  }

  @Test
  void tellsEachReviewerHowToReadWorktreePastTheFirst() {
    String shared = MasterPanel.shared("judge hard", "never commit unasked", false, true);

    assertThat(shared).contains("`git -C <worktree>`");
  }

  @Test
  void letsReviewerStandingInForTheHumanDecideWhatItWouldHaveAsked() {
    String shared = MasterPanel.shared("judge hard", "never commit unasked", true, true);

    assertThat(shared).contains("never answer question");
  }

  @Test
  void quotesTheTicketReadForTheRoundToEachReviewer() {

    String prompt =
        MasterPanel.quote(
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

    String prompt = MasterPanel.quote("ABC-1", task, emptyRead);

    assertThat(prompt).contains("<probes>\ncommand ./gradlew test — passed\nexit 0\n</probes>");
  }

  @Test
  void showsThePlanReaderTheProbesJagtWillRun() {
    TaskState task =
        TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.PLAN_PENDING)
            .probes(List.of(Probe.specified(Probe.Kind.REF, List.of("refs/tags/v1"), 0)))
            .build();

    String prompt =
        MasterPanel.planPrompt("ABC-1", task, "judge hard", "1. tag v1", emptyRead, false, true);

    assertThat(prompt).contains("<probes>\nref refs/tags/v1\n</probes>");
  }

  @Test
  void judgesThePlanAgainstTheTicketItQuotes() {

    String prompt =
        MasterPanel.planPrompt(
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
        MasterPanel.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, false);

    assertThat(prompt).doesNotContain("tracker tools").contains("The ticket was not read");
  }

  @Test
  void sendsPlanReaderLoadingServerToItsMcpToolsWhicheverServerThatIs() {

    String prompt =
        MasterPanel.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, true);

    assertThat(prompt)
        .contains("Read the ticket with your MCP tools")
        .doesNotContain("tracker tools");
  }

  @Test
  void tellsPlanReaderHowToWriteItsGitReads() {

    String prompt =
        MasterPanel.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, true);

    assertThat(prompt).contains("`--no-ext-diff --no-textconv`", "`git -C <worktree>`");
  }

  @Test
  void tellsTheMasterAnsweringQuestionHowToWriteItsGitReads() {

    String prompt =
        MasterPanel.answerPrompt(
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
        MasterPanel.answerPrompt(
            "ABC-1",
            inProgress,
            briefsPair,
            new MasterPanel.Asked("outcome=question — keep v2?", "", List.of(), false),
            false);

    assertThat(prompt).contains("The ticket was not read").doesNotContain("Read the ticket");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = ';',
      value = {
        "false;call any premise resting on the ticket unproven",
        "true;Read the ticket with your MCP tools"
      })
  void tellsReviewerWhatToDoWithTheTicketTheRoundDoesNotQuote(boolean loadsServer, String told) {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(reviews.loadsMcpServer()).thenReturn(loadsServer);
    when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("stopped"));

    panel.review("ABC-1", reviewPending, judge);

    ArgumentCaptor<RoundReviewer.Round> round = ArgumentCaptor.captor();
    verify(reviews).review(eq("ABC-1"), round.capture());
    assertThat(round.getValue().shared()).doesNotContain("tracker tools").contains(told);
  }

  @Test
  void quotesTheDiffReadForTheRoundToEachReviewer() {

    String prompt =
        MasterPanel.quote(
            "ABC-1", reviewPending, new MasterPanel.RoundRead("", "+int x;\n", "", List.of(), ""));

    assertThat(prompt).contains("<diff>\n+int x;\n</diff>");
  }

  @Test
  void givesEveryRoleTheRoundAsTheSamePrefixSoItIsReadFromCache() {
    when(briefs.master(eq("ABC-1"), any()))
        .thenReturn(
            Optional.of(
                """
                | role | question |
                | chaplain | should it exist |
                | developer | is the code right |\
                """));
    when(quotes.round("ABC-1", reviewPending))
        .thenReturn(Optional.of(new MasterPanel.RoundRead("", "+int x;\n", "", List.of(), "")));
    when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("stopped"));

    panel.review("ABC-1", reviewPending, judge);

    ArgumentCaptor<RoundReviewer.Round> round = ArgumentCaptor.captor();
    verify(reviews, times(2)).review(eq("ABC-1"), round.capture());
    assertThat(round.getAllValues())
        .extracting(RoundReviewer.Round::shared)
        .containsOnly(round.getAllValues().getFirst().shared())
        .allMatch(shared -> shared.contains("<diff>\n+int x;\n</diff>"));
  }

  @Test
  void startsTheOtherRolesOnlyOnceTheFirstHasAnswered() {
    when(briefs.master(eq("ABC-1"), any()))
        .thenReturn(
            Optional.of(
                """
                | role | question |
                | chaplain | should it exist |
                | developer | is the code right |\
                """));
    CountDownLatch developerStarted = new CountDownLatch(1);
    when(reviews.review(eq("ABC-1"), argThat(round -> round.prompt().contains("chaplain"))))
        .thenAnswer(call ->
            Judgement.failed(
                "developer started: " + developerStarted.await(300, TimeUnit.MILLISECONDS)));
    when(reviews.review(eq("ABC-1"), argThat(round -> round.prompt().contains("developer"))))
        .thenAnswer(call -> {
          developerStarted.countDown();
          return Judgement.failed("stopped");
        });

    panel.review("ABC-1", reviewPending, judge);

    assertThat(worktree.resolve(MasterReview.FILE)).content().contains("developer started: false");
  }

  @Test
  void asksTheMasterToDecideTheSessionsQuestionRatherThanDeferIt() {

    String prompt =
        MasterPanel.answerPrompt(
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
        MasterPanel.answerPrompt(
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
        MasterPanel.answerPrompt(
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
        MasterPanel.answerPrompt(
            "ABC-1",
            reviewPending,
            briefsPair,
            new MasterPanel.Asked("outcome=question — keep v2?", "", List.of(), false),
            false);
    String review = MasterPanel.shared("judge hard", "never commit unasked", true, true);

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
        MasterPanel.planPrompt(
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
        MasterPanel.planPrompt(
            "ABC-1", planPending, "judge hard", "1. add the v3 route", emptyRead, false, true);

    assertThat(prompt)
        .contains(
            "only for correctness or a stated requirement",
            "wording, glossary and style are noise");
  }

  @Test
  void tagsTheRoleAndForbidsPreambleInEveryMasterPrompt() {
    MasterPanel.RoundRead read = emptyRead;

    assertThat(MasterPanel.prompt(TWO.getFirst()))
        .startsWith("<role>You are the chaplain")
        .endsWith("</role>\nRespond directly, no preamble.");
    assertThat(MasterPanel.planPrompt("ABC-1", planPending, "judge hard", "", read, false, true))
        .contains("<role>You judge the plan of task ABC-1")
        .endsWith("Respond directly, no preamble.");
    assertThat(
            MasterPanel.answerPrompt(
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
        MasterPanel.quote(
            "ABC-1",
            reviewPending,
            new MasterPanel.RoundRead(
                "", "", "- keep the extraction per the human", List.of(), ""));

    assertThat(prompt).contains("binding").contains("- keep the extraction per the human");
  }

  @Test
  void quotesWhatTheHumanTypedToTheSessionToEachReviewer() {

    String prompt =
        MasterPanel.quote(
            "ABC-1",
            reviewPending,
            new MasterPanel.RoundRead(
                "", "", "- keep the old field name", List.of("rename it everywhere"), ""));

    assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
  }

  @Test
  void quotesTheSessionsNotesSoReviewerCanCheckWhatItDisputed() {

    String prompt =
        MasterPanel.quote(
            "ABC-1",
            reviewPending,
            new MasterPanel.RoundRead(
                "", "", "", List.of(), "disputed: drop v2 — Api.java:12 has no caller"));

    assertThat(prompt)
        .contains(
            "<session_notes>\ndisputed: drop v2 — Api.java:12 has no caller\n</session_notes>");
  }

  @Test
  void asksTheSessionForEvidenceWhereRoleCouldNotProveItsFinding() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        "not ready",
                        List.of(
                            new Finding(
                                "Api.java",
                                "which caller still reads v2",
                                "evidence needed",
                                "unproven",
                                "",
                                "")),
                        "",
                        List.of(),
                        ""),
                    ready)),
            List.of());

    assertThat(file)
        .contains("- [chaplain] Api.java — show: which caller still reads v2 (evidence needed)");
  }

  @Test
  void quotesWhatTheHumanTypedToTheSessionWhenAnsweringInTheirStead() {

    String prompt =
        MasterPanel.answerPrompt(
            "ABC-1",
            inProgress,
            briefsPair,
            new MasterPanel.Asked(
                "outcome=question — rename the field?", "", List.of("rename it everywhere"), false),
            false);

    assertThat(prompt).contains("<human_said>\nrename it everywhere\n</human_said>");
  }

  @Test
  void asksNoReviewerWhereWhatTheHumanTypedCouldNotBeRead() throws Exception {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.round("ABC-1", reviewPending)).thenReturn(Optional.empty());

    panel.review("ABC-1", reviewPending, act);

    verify(reviews, never()).review(any(), any());
    assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
        .contains(
            "could not read the round: what the human typed to the session could not be read");
  }

  @Test
  void writesNoVerdictOnRoundWhoseReadStoppedWithJagt() {
    when(briefs.master(eq("ABC-1"), any()))
        .thenReturn(Optional.of("| role | question |\n| QA | is it tested |"));
    when(reviews.review(eq("ABC-1"), any())).thenThrow(new Processes.Stopped(List.of("claude")));

    assertThatThrownBy(() -> panel.review("ABC-1", reviewPending, judge))
        .isInstanceOf(Processes.Stopped.class);

    assertThat(worktree.resolve(MasterReview.FILE)).doesNotExist();
  }

  @Test
  void readsTheRoundByTheAuthorsRolesWhereTheMastersBriefNamesNone() {
    String authorBrief =
        """
        | role | the question it asks |
        |------|----------------------|
        | developer | is the code right |
        """;

    assertThat(MasterPanel.roles("# Brief\n\nJudge hard.\n", authorBrief))
        .extracting(MasterPanel.Role::name)
        .containsExactly("developer");
  }

  @Test
  void readsEveryRowOfTheBriefsRoleTableAsItsOwnReviewer() {
    String brief =
        """
        # Brief

        | role | the question it asks |
        |------|----------------------|
        | chaplain | should this exist at all |
        | QA | is it tested right |

        Afterwards.
        """;

    assertThat(MasterPanel.roles(brief, ""))
        .extracting(MasterPanel.Role::name)
        .containsExactly("chaplain", "QA");
  }

  @Test
  void readsRoleWithTheModelItsRowNames() {
    String brief =
        """
        | role | the question it asks | model |
        |------|----------------------|-------|
        | architect | is the architecture held | opus |
        | QA | is it tested right | |
        """;

    assertThat(MasterPanel.roles(brief, ""))
        .extracting(role -> role.modelOr("sonnet"))
        .containsExactly("opus", "sonnet");
  }

  @Test
  void readsTheModelAndTheAppliesGlobsByTheirColumnsNamesInAnyOrder() {
    String brief =
        """
        | role | the question it asks | applies | model |
        |------|----------------------|---------|-------|
        | designer | is the ideology intact | `static/**`, docs/{rules,usage}.md | opus |
        """;

    assertThat(MasterPanel.roles(brief, ""))
        .containsExactly(
            new MasterPanel.Role(
                "designer",
                "is the ideology intact",
                "opus",
                List.of("static/**", "docs/{rules,usage}.md")));
  }

  @Test
  void skipsTheReadOfRoleWhoseGlobsMatchNoChangedPathAndSaysSoInTheReviewFile() {
    when(briefs.master(eq("ABC-1"), any()))
        .thenReturn(
            Optional.of(
                """
                | role | question | applies |
                | designer | is the mark worth it | static/** |
                | QA | is it tested |\
                """));
    when(quotes.paths(reviewPending)).thenReturn(Optional.of(List.of("src/Main.java")));

    panel.review("ABC-1", reviewPending, judge);

    verify(reviews, times(1)).review(eq("ABC-1"), argThat(round -> round.prompt().contains("QA")));
    verify(reviews, never())
        .review(eq("ABC-1"), argThat(round -> round.prompt().contains("designer")));
    assertThat(worktree.resolve(MasterReview.FILE))
        .content()
        .contains("# skipped [designer] no changed path matches static/**\n")
        .doesNotContain("skipped [QA]")
        .endsWith("VERDICT: ready\n");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = ';',
      value = {"docs/rules.md;config/app.yml;false", "README.md;src/Main.java;true"})
  void skipsRoleWhereEveryChangedPathIsOneItNeverReads(String first, String second, boolean read) {
    MasterPanel.Role qa =
        new MasterPanel.Role(
            "QA", "is it tested", "", List.of("**/*.java", "!**/*.md", "!**/*.yml"));

    assertThat(qa.reads(Optional.of(List.of(first, second)))).isEqualTo(read);
  }

  @Test
  void readsRoundByRoleWithGlobsWhereTheChangedPathsCouldNotBeRead() {
    when(briefs.master(eq("ABC-1"), any()))
        .thenReturn(
            Optional.of(
                """
                | role | question | applies |
                | designer | is the mark worth it | static/** |
                | QA | is it tested |\
                """));
    when(quotes.paths(reviewPending)).thenReturn(Optional.empty());

    panel.review("ABC-1", reviewPending, judge);

    verify(reviews).review(eq("ABC-1"), argThat(round -> round.prompt().contains("designer")));
  }

  @Test
  void readsRoundByEveryRoleWhereNoRolesGlobsMatchIt() {
    when(briefs.master(eq("ABC-1"), any()))
        .thenReturn(
            Optional.of(
                "| role | question | applies |\n| designer | is the mark worth it | static/** |"));
    when(quotes.paths(reviewPending)).thenReturn(Optional.of(List.of("src/Main.java")));

    panel.review("ABC-1", reviewPending, judge);

    verify(reviews).review(eq("ABC-1"), argThat(round -> round.prompt().contains("designer")));
  }

  @Test
  void passesRoundOnlyWhereEveryRoleFoundNothing() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        "ready",
                        List.of(),
                        "",
                        List.of(new Premise("tests pass", "gradle test: 2 passed")),
                        ""),
                    ready)),
            List.of());

    assertThat(file).doesNotContain("unproven").endsWith("VERDICT: ready\n");
  }

  @Test
  void sendsBackRoundOneRoleFoundFaultWithWhateverTheOthersSaid() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        "not ready",
                        List.of(
                            new Finding(
                                "QuoteMock.java",
                                "v2 dropped",
                                "removed contract",
                                "blocking",
                                "",
                                "")),
                        "",
                        List.of(),
                        ""),
                    ready)),
            List.of());

    assertThat(file)
        .contains("- [chaplain] QuoteMock.java — v2 dropped (removed contract)")
        .endsWith("VERDICT: not ready\n");
  }

  @Test
  void passesRoundWhoseOnlyDoubtIsClaimTheReviewerDidNotProve() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        "ready",
                        List.of(),
                        "",
                        List.of(new Premise("v2 and v3 cannot be told apart", "")),
                        ""),
                    ready)),
            List.of());

    assertThat(file)
        .contains("# unproven [chaplain] v2 and v3 cannot be told apart")
        .endsWith("VERDICT: ready\n");
  }

  @Test
  void passesRoundWhoseOnlyFindingsAreAdvice() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        "not ready",
                        List.of(
                            new Finding("Foo.java", "rename present", "naming", "noise", "", "")),
                        "",
                        List.of(),
                        ""),
                    ready)),
            List.of());

    assertThat(file)
        .contains("# advice [chaplain] Foo.java — rename present")
        .endsWith("VERDICT: ready\n");
  }

  @Test
  void putsAnyRolesQuestionToTheHumanOnTheLineAboveTheVerdict() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement(
                        "",
                        "question",
                        List.of(),
                        "Add v3 beside v2, or replace it?",
                        List.of(),
                        ""),
                    ready)),
            List.of());

    assertThat(file).endsWith("Add v3 beside v2, or replace it?\n\nVERDICT: question\n");
  }

  @Test
  void asksTheHumanWhenRoleCouldNotReadTheRound() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO, List.of(Judgement.failed("no tracker tool in this session"), ready)),
            List.of());

    assertThat(file)
        .endsWith(
            """
            The chaplain could not read the round: no tracker tool in this session

            VERDICT: question
            """);
  }

  @Test
  void writesNothingAndAsksNoReviewerWhereTheBriefCouldNotBeRead() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.empty());

    boolean written = panel.review("ABC-1", reviewPending, act);

    assertThat(written).isFalse();
    verify(reviews, never()).review(any(), any());
  }

  @Test
  void saysSoAfterWritingTheReviewFileIntoTheWorktree() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.round("ABC-1", reviewPending)).thenReturn(Optional.empty());

    boolean written = panel.review("ABC-1", reviewPending, act);

    assertThat(written).isTrue();
    assertThat(worktree.resolve(MasterReview.FILE)).exists();
  }

  @Test
  void saysNothingWasWrittenWhereTheWorktreeCannotTakeTheReviewFile() {
    TaskState task =
        TaskState.builder("proj", worktree.resolve("gone").toString(), TaskStatus.REVIEW_PENDING)
            .build();
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.round("ABC-1", task)).thenReturn(Optional.empty());

    boolean written = panel.review("ABC-1", task, act);

    assertThat(written).isFalse();
  }

  @Test
  void writesNothingForPlanWhereTheBriefCouldNotBeRead() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.empty());

    boolean written = panel.plan("ABC-1", planPending, act);

    assertThat(written).isFalse();
    assertThat(worktree.resolve(MasterReview.FILE)).doesNotExist();
  }

  @Test
  void writesReadyVerdictForPlanThePlannerFoundNothingWrongWith() throws Exception {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(quotes.plan("ABC-1", planPending)).thenReturn(Optional.of(emptyRead));
    when(quotes.planFile(planPending)).thenReturn("1. add the v3 route");

    boolean written = panel.plan("ABC-1", planPending, act);

    assertThat(written).isTrue();
    assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
        .startsWith("# ABC-1 — master review")
        .endsWith("VERDICT: ready\n");
  }

  @Test
  void asksNoPlannerWhereWhatTheHumanTypedCouldNotBeRead() throws Exception {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(quotes.plan("ABC-1", planPending)).thenReturn(Optional.empty());

    panel.plan("ABC-1", planPending, act);

    verify(reviews, never()).review(any(), any());
    assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
        .contains(
            "could not read the round: what the human typed to the session could not be read");
  }

  @Test
  void answersQuestionWithTheFindingsTheReviewerReturned() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(reviews.review(eq("ABC-1"), any()))
        .thenReturn(
            new Judgement(
                "",
                "answer",
                List.of(new Finding("", "keep v2 beside v3", "", "blocking", "", "")),
                "",
                List.of(),
                "do proj drop the old keys"));

    MasterPanel.Answer decision =
        panel.answer("ABC-1", inProgress, "outcome=question — keep v2?", act, false);

    assertThat(decision)
        .isEqualTo(new MasterPanel.Decision("keep v2 beside v3", "do proj drop the old keys"));
  }

  @Test
  void answersNothingWhereTheReviewerNamedNothing() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(reviews.review(eq("ABC-1"), any()))
        .thenReturn(new Judgement("", "answer", List.of(), "", List.of(), ""));

    MasterPanel.Answer decision =
        panel.answer("ABC-1", inProgress, "outcome=question — keep v2?", act, false);

    assertThat(decision).isInstanceOf(MasterPanel.Undecided.class);
  }

  @Test
  void namesTheReviewersFailureAsWhyNothingWasDecided() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(reviews.review(eq("ABC-1"), any()))
        .thenReturn(
            new Judgement(
                "stopped",
                "",
                List.of(new Finding("", "keep v2", "", "blocking", "", "")),
                "",
                List.of(),
                ""));

    MasterPanel.Answer decision =
        panel.answer("ABC-1", inProgress, "outcome=question — keep v2?", act, false);

    assertThat(decision).isEqualTo(new MasterPanel.Undecided("stopped"));
  }

  @Test
  void answersNothingWhereTheBriefOrWhatTheHumanSaidCouldNotBeRead() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(quotes.question(inProgress)).thenReturn(Optional.empty());

    MasterPanel.Answer decision =
        panel.answer("ABC-1", inProgress, "outcome=question — keep v2?", act, false);

    assertThat(decision).isInstanceOf(MasterPanel.Undecided.class);
    verify(reviews, never()).review(any(), any());
  }

  @Test
  void quotesTheSessionsNotesToThePlanReader() {

    String prompt =
        MasterPanel.planPrompt(
            "ABC-1",
            planPending,
            "judge hard",
            "1. add the v3 route",
            new MasterPanel.RoundRead("", "", "", List.of(), "disputed: the ticket line 2"),
            false,
            true);

    assertThat(prompt).contains("<session_notes>\ndisputed: the ticket line 2\n</session_notes>");
  }

  @Test
  void holdsPlanReaderToTheFactThatTheSessionWroteNoPlan() {

    String prompt =
        MasterPanel.planPrompt("ABC-1", planPending, "judge hard", "", emptyRead, false, true);

    assertThat(prompt)
        .contains("The session wrote no plan.md: that alone is not ready.")
        .doesNotContain("<plan>");
  }

  @Test
  void letsPlanReaderStandingInForTheHumanDecideWhatTheTicketLeavesOpen() {

    String prompt =
        MasterPanel.planPrompt(
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
        MasterPanel.answerPrompt(
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

    String prompt = MasterPanel.quote("ABC-1", task, emptyRead);

    assertThat(prompt)
        .contains("ticket https://tracker.example/ABC-1.")
        .doesNotContain("ticket none");
  }

  @Test
  void doesNotReadAgainRoundWhoseReadyVerdictStillStands() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.carried("ABC-1", reviewPending, "")).thenReturn(true);

    boolean written = panel.review("ABC-1", reviewPending, act);

    assertThat(written).isTrue();
    verify(reviews, never()).review(any(), any());
    assertThat(worktree.resolve(MasterReview.FILE)).doesNotExist();
  }

  @Test
  void namesWhyAndOverWhatRoundWasReadInTheLog() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.inputs("ABC-1", reviewPending)).thenReturn(Optional.of("in1"));
    when(quotes.trigger(reviewPending, "in1")).thenReturn("retry");
    when(quotes.fingerprint(reviewPending, "in1")).thenReturn("fp1");
    when(quotes.round("ABC-1", reviewPending)).thenReturn(Optional.empty());
    try (FakeLogCapture log = new FakeLogCapture(MasterPanel.class)) {

      panel.review("ABC-1", reviewPending, act);

      assertThat(log.events())
          .filteredOn(event -> "master review asked".equals(event.getMessage()))
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("trigger=retry", "fingerprint=fp1");
    }
  }

  @Test
  void namesPlanReadAsSuchInTheLog() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.inputs("ABC-1", planPending)).thenReturn(Optional.of("in1"));
    when(quotes.planFingerprint(planPending, "in1")).thenReturn("fp2");
    when(quotes.plan("ABC-1", planPending)).thenReturn(Optional.empty());
    try (FakeLogCapture log = new FakeLogCapture(MasterPanel.class)) {

      panel.plan("ABC-1", planPending, act);

      assertThat(log.events())
          .filteredOn(event -> "master review asked".equals(event.getMessage()))
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("trigger=plan", "fingerprint=fp2");
    }
  }

  @Test
  void asksTheHumanWhereReviewerThrewInsteadOfAnswering() throws Exception {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(reviews.review(eq("ABC-1"), any())).thenThrow(new IllegalStateException("cli gone"));

    panel.review("ABC-1", reviewPending, act);

    assertThat(Files.readString(worktree.resolve(MasterReview.FILE)))
        .contains(
            "The reviewer could not read the round: the review threw"
                + " java.lang.IllegalStateException: cli gone")
        .endsWith("VERDICT: question\n");
  }

  @Test
  void storesTheOwnDiffAndTheHandBackItWasAskedAboutWhereEveryReviewerReadIt() {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(new Turn("round-not-ready", 5, "", 5_000, Map.of(), List.of()));
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.round("ABC-1", task)).thenReturn(Optional.of(emptyRead));
    when(quotes.ownDiff(task)).thenReturn(Optional.of("abc123"));

    state.putTask("ABC-1", task);

    panel.review("ABC-1", task, act);

    assertThat(stored.of(state.task("ABC-1").orElseThrow()))
        .get()
        .returns("abc123", MasterReview.Verdict::ownDiff)
        .returns(5L, MasterReview.Verdict::round);
  }

  @Test
  void storesNoOwnDiffWhereReviewerCouldNotReadTheRound() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    when(quotes.ownDiff(reviewPending)).thenReturn(Optional.of("abc123"));
    when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("no tracker tool"));

    state.putTask("ABC-1", reviewPending);

    panel.review("ABC-1", reviewPending, act);

    assertThat(stored.of(state.task("ABC-1").orElseThrow()))
        .get()
        .returns("", MasterReview.Verdict::ownDiff);
  }

  @Test
  void stopsAndSaysNothingWasWrittenWhereTheCallerIsInterruptedMidRound() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("| QA | is it tested right |"));
    CountDownLatch stillReviewing = new CountDownLatch(1);
    when(reviews.review(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          stillReviewing.await(2, TimeUnit.SECONDS);
          return ready;
        });
    Thread.currentThread().interrupt();

    boolean written = panel.review("ABC-1", reviewPending, act);

    assertThat(Thread.interrupted()).isTrue();
    assertThat(written).isFalse();
    assertThat(worktree.resolve(MasterReview.FILE)).doesNotExist();
  }

  @Test
  void saysNothingWasWrittenForPlanWhereTheWorktreeCannotTakeTheFile() {
    TaskState task =
        TaskState.builder("proj", worktree.resolve("gone").toString(), TaskStatus.PLAN_PENDING)
            .build();
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(quotes.plan("ABC-1", task)).thenReturn(Optional.empty());

    boolean written = panel.plan("ABC-1", task, act);

    assertThat(written).isFalse();
  }

  @Test
  void namesEveryQuestionWhereSeveralRolesAsk() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement("", "question", List.of(), "Add v3 beside v2?", List.of(), ""),
                    new Judgement("", "question", List.of(), "Drop the old keys?", List.of(), ""))),
            List.of());

    assertThat(file)
        .endsWith("- also asked: Drop the old keys?\nAdd v3 beside v2?\n\nVERDICT: question\n");
  }

  @Test
  void readsNoModelFromTheQuestionColumnWhateverItsHeaderIsCalled() {
    String brief =
        """
        | role | model |
        |------|-------|
        | architect | opus |
        """;

    assertThat(MasterPanel.roles(brief, ""))
        .extracting(role -> role.modelOr("sonnet"))
        .containsExactly("sonnet");
  }

  @Test
  void readsNoModelFromRowShorterThanTheHeader() {
    String brief =
        """
        | role | the question it asks | model |
        |------|----------------------|-------|
        | architect | is the architecture held |
        """;

    assertThat(MasterPanel.roles(brief, ""))
        .extracting(role -> role.modelOr("sonnet"))
        .containsExactly("sonnet");
  }

  @Test
  void namesEveryWorktreeOfTheTaskToTheMasterAnsweringQuestion() {
    TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(quotes.question(task)).thenReturn(Optional.of(emptyRead));
    when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("stopped"));

    panel.answer("ABC-1", task, "keep v2?", act, false);

    ArgumentCaptor<RoundReviewer.Round> asked = ArgumentCaptor.captor();
    verify(reviews).review(eq("ABC-1"), asked.capture());
    assertThat(asked.getValue().worktrees()).containsExactly(Path.of("/wt/ABC-1-proj"));
  }

  @Test
  void readsNoChangedPathsWhereNoRoleScopesItselfByGlobs() {
    when(briefs.master(eq("ABC-1"), any()))
        .thenReturn(
            Optional.of(
                """
                | role | question |
                | chaplain | should it exist |
                | developer | is the code right |\
                """));

    panel.review("ABC-1", reviewPending, judge);

    verify(quotes, never()).paths(reviewPending);
  }

  @Test
  void listsNoUnprovenPremiseOfRoleWhoseReadFailed() {
    String file =
        MasterPanel.verdictFile(
            "ABC-42",
            MasterPanel.reads(
                TWO,
                List.of(
                    new Judgement(
                        "read failed",
                        "",
                        List.of(),
                        "",
                        List.of(new Premise("tests pass", "")),
                        ""),
                    ready)),
            List.of());

    assertThat(file).doesNotContain("unproven [chaplain]");
  }
}
