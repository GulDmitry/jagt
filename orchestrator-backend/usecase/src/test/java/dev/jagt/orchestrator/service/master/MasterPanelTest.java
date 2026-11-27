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
import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeHost;
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

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});

  @TempDir Path stateRoot;
  @TempDir Path worktree;

  private final ChargedReviews reviews = mock(ChargedReviews.class);
  private final MasterBriefs briefs = mock(MasterBriefs.class);
  private final RoundQuotes quotes = mock(RoundQuotes.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final ConfigFile.ArbiterConfig judge =
      new ConfigFile.ArbiterConfig("judge", null, null, null, null);
  private final ConfigFile.ArbiterConfig act =
      new ConfigFile.ArbiterConfig("act", null, null, null, null);
  private final MasterPanel.RoundRead emptyRead =
      new MasterPanel.RoundRead("", "", "", List.of(), "");
  private final Judgement ready =
      new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE);

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
                    .withStateFile(stateRoot.resolve("state.json").toString()),
                FakeHost.machine()),
            reducer,
            event -> {});
    stored = new MasterReview(state);
    panel = new MasterPanel(reviews, briefs, quotes, stored, new RoleYields(configService));
    reviewPending =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
    planPending = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
    inProgress = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
    when(configService.load()).thenReturn(ConfigFile.defaults());
    when(briefs.author()).thenReturn("");
    when(quotes.round("ABC-1", reviewPending)).thenReturn(Optional.of(emptyRead));
    when(quotes.question(inProgress)).thenReturn(Optional.of(emptyRead));
    when(reviews.review(eq("ABC-1"), any())).thenReturn(ready);
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
                List.of("static/**", "docs/{rules,usage}.md"),
                ""));
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
            "QA", "is it tested", "", List.of("**/*.java", "!**/*.md", "!**/*.yml"), "");

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
                Verdict.READY,
                List.of(new Finding("", "keep v2 beside v3", "", "blocking", "", "")),
                "",
                List.of(),
                "do proj drop the old keys",
                Press.NONE));

    MasterPanel.Answer decision =
        panel.answer("ABC-1", inProgress, "outcome=question — keep v2?", act, false);

    assertThat(decision)
        .isEqualTo(
            new MasterPanel.Decision("keep v2 beside v3", "do proj drop the old keys", Press.NONE));
  }

  @Test
  void answersWithTheMoveTheReviewerNamed() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(reviews.review(eq("ABC-1"), any()))
        .thenReturn(
            new Judgement(
                "",
                Verdict.READY,
                List.of(new Finding("", "ship it, the tree is green", "", "noise", "", "")),
                "",
                List.of(),
                "",
                new Press.Move("ship")));

    MasterPanel.Answer decision =
        panel.answer("ABC-1", inProgress, "outcome=question — ship to push?", act, false);

    assertThat(decision)
        .isEqualTo(
            new MasterPanel.Decision("ship it, the tree is green", "", new Press.Move("ship")));
  }

  @Test
  void answersNothingWhereTheReviewerNamedNothing() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(reviews.review(eq("ABC-1"), any()))
        .thenReturn(new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE));

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
                Verdict.NOT_READY,
                List.of(new Finding("", "keep v2", "", "blocking", "", "")),
                "",
                List.of(),
                "",
                Press.NONE));

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
  void leavesTheQuestionToTheHumanWhereTheTrackerCouldNotSayAboutNamedTicket() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(quotes.named("ABC-1", "Is ABC-7 closed?"))
        .thenReturn(List.of(new NamedTickets.Fact("ABC-7", "", false)));

    MasterPanel.Answer decision = panel.answer("ABC-1", inProgress, "Is ABC-7 closed?", act, false);

    assertThat(decision)
        .isEqualTo(new MasterPanel.Undecided("the tracker could not say about ABC-7"));
    verify(reviews, never()).review(any(), any());
  }

  @Test
  void givesTheMasterTheStatusJagtReadForNamedTicket() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of("judge hard"));
    when(quotes.named("ABC-1", "Is ABC-7 closed?"))
        .thenReturn(List.of(new NamedTickets.Fact("ABC-7", "Done", true)));

    panel.answer("ABC-1", inProgress, "Is ABC-7 closed?", act, false);

    ArgumentCaptor<RoundReviewer.Round> asked = ArgumentCaptor.captor();
    verify(reviews).review(eq("ABC-1"), asked.capture());
    assertThat(asked.getValue().prompt())
        .contains("ABC-7: status \"Done\", at the stage that closes work: yes");
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
}
