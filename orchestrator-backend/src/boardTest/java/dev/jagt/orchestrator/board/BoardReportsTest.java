package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static dev.jagt.orchestrator.board.TestReplyDraftBuilder.replyDraft;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardReportsTest extends BoardPageContext {

  @Autowired
  BoardReportsTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void theLogButtonKeepsItsNameAndCarriesTheLastMessageInItsHover() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.execute("ABC-1", TaskAction.FOCUS, null))
        .thenReturn("Focused ABC-1 — its window is in front.");

    Page page = openLiveBoard();
    button(page, "Focus").click();

    assertThat(page.locator("#show-log")).hasText("log");
    assertThat(page.locator("#show-log"))
        .hasAttribute("data-tip", Pattern.compile("Focused ABC-1 — its window is in front."));
  }

  @Test
  void reportThatAnswersForOneTaskGetsNoButtonInTheBarOfReports() {
    Page page = openLiveBoard();

    assertThat(page.locator("#show-stats")).hasCount(1);
    assertThat(page.locator("#show-replies")).hasCount(0);
  }

  @Test
  void draftedRepliesLiveOnTheirTabAndTheCardCarriesNoRepliesButton() throws IOException {
    replyDraft(root).withKey("ABC-20").withText("## thread 1\nagreed, fixed\n").build();
    putReviewTask("ABC-20");

    Page page = openLiveBoard();
    page.locator("#pane .tabs button[data-tab=round]").click();

    assertThat(page.locator("#pane .round")).containsText("agreed, fixed");
    assertThat(page.locator("article .offer")).hasCount(0);
  }

  @Test
  void noRepliesButtonShowsOnTheCardWhileTheVerbsAreStillArriving() throws IOException {
    replyDraft(root).withKey("ABC-19").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-19");
    List<Route> verbs = new java.util.ArrayList<>();
    Page page = session.newPage();
    page.route("**/api/commands", verbs::add);

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("#pane article .alias")).hasText("a19");
    assertThat(page.locator("article .offer")).hasCount(0);
  }

  @Test
  void lineSaidAtAnOpenRoundReachesTheSessionOfTheTaskItIsAbout() throws IOException {
    replyDraft(root).withKey("ABC-5").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-5");
    when(sessions.say("a5", "no, answer 1 differently")).thenReturn("Said to the agent.");

    Page page = openLiveBoard();
    ask(page, "replies a5");
    page.locator("#say").fill("no, answer 1 differently");
    page.locator("#say").press("Enter");

    assertThat(page.locator("#said")).isVisible();
    verify(sessions).say("a5", "no, answer 1 differently");
  }

  @Test
  void reportThatIsNotAboutOneTaskOffersNoLineEvenWhenAnArgumentNamesOne() {
    state.putTask("ABC-12", alphaTask("ABC-12", TaskStatus.REVIEW_PENDING).build());

    Page page = openLiveBoard();
    ask(page, "activity a12");

    assertThat(page.locator("#report-title")).containsText("activity");
    assertThat(page.locator("#report-line")).isHidden();
  }

  @Test
  void lineTypedAtOneRoundIsNotCarriedIntoTheNextOneOpened() throws IOException {
    replyDraft(root).withKey("ABC-13").withText("## thread 1\nFIXED - Renamed it.\n").build();
    replyDraft(root).withKey("ABC-14").withText("## thread 1\nFIXED - Pinned the count.\n").build();
    putReviewTask("ABC-13");
    state.putTask(
        "ABC-14",
        alphaTask("ABC-14", TaskStatus.REVIEW_PENDING).mrUrl("https://host.example/mr/8").build());

    Page page = openLiveBoard();
    ask(page, "replies a13");
    page.locator("#say").fill("no, answer 1 differently");
    page.locator("#close-report").click();
    ask(page, "replies a14");

    assertThat(page.locator("#say")).hasValue("");
  }

  @Test
  void whatTheWaitingRingMeansIsReadableOverTheDialogItPulsesIn() throws IOException {
    replyDraft(root).withKey("ABC-15").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-15");
    when(sessions.say("a15", "no, answer 1 differently")).thenReturn("Said to the agent.");

    Page page = openLiveBoard();
    ask(page, "replies a15");
    page.locator("#say").fill("no, answer 1 differently");
    page.locator("#say").press("Enter");
    page.locator("#said").hover();

    assertThat(page.locator("#report #tip")).isVisible();
  }

  @Test
  void anOpenRoundNobodyHasAnsweredYetShowsNothingWaiting() throws IOException {
    replyDraft(root).withKey("ABC-11").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-11");

    Page page = openLiveBoard();
    ask(page, "replies a11");

    assertThat(page.locator("#said")).isHidden();
  }

  @Test
  void reportAnsweringForEveryTaskHasNoSessionToSayAnythingTo() {
    state.putTask("ABC-8", alphaTask("ABC-8", TaskStatus.REVIEW_PENDING).build());

    Page page = openLiveBoard();
    ask(page, "replies");

    assertThat(page.locator("#report")).isVisible();
    assertThat(page.locator("#report-line")).isHidden();
  }

  @Test
  void anOpenRoundIsReadAgainWhenTheAgentAnswersUnderIt() throws IOException {
    replyDraft(root).withKey("ABC-9").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-9");

    Page page = openLiveBoard();
    ask(page, "replies a9");
    replyDraft(root)
        .withKey("ABC-9")
        .withText("## thread 1\nNO CHANGE - The name is the one the caller uses.\n")
        .build();
    state.putTask(
        "ABC-9",
        state
            .task("ABC-9")
            .orElseThrow()
            .withStatus(TaskStatus.REVIEW_PENDING, "answered", System.currentTimeMillis()));

    assertThat(page.locator("#report-body")).containsText("The name is the one the caller uses.");
  }

  @Test
  void roundRewrittenWhileItsReportWasStillBeingReadShowsWhatTheFileSaysNow() throws IOException {
    replyDraft(root).withKey("ABC-10").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-10");
    Page page = openLiveBoard();
    page.route(
        "**/api/commands/replies**",
        route -> {
          APIResponse read = route.fetch();
          try {
            replyDraft(root)
                .withKey("ABC-10")
                .withText("## thread 1\nNO CHANGE - The name is the one the caller uses.\n")
                .build();
          } catch (IOException cannotStageTheRewrite) {
            throw new UncheckedIOException(cannotStageTheRewrite);
          }
          route.fulfill(new Route.FulfillOptions().setResponse(read));
        });

    ask(page, "replies a10");

    assertThat(page.locator("#report-body")).containsText("The name is the one the caller uses.");
  }

  @Test
  void reportsOwnLinksAreFollowableAndNothingElseBecomesOne() throws IOException {
    Path worktree = Files.createDirectories(root.resolve("ABC-6-alpha"));
    Files.writeString(
        worktree.resolve("review_replies.md"),
        """
        ## thread 1
        > see https://host.example/mr/7#note_8708.
        FIXED - javascript:alert(1) stays text.
        """);
    putReviewTask("ABC-6");

    Page page = openLiveBoard();
    ask(page, "replies a6");

    assertThat(page.locator("#report-body a"))
        .hasAttribute("href", "https://host.example/mr/7#note_8708");
    assertThat(page.locator("#report-body")).containsText("javascript:alert(1) stays text.");
    assertThat(page.locator("#report-body a")).hasCount(1);
  }

  @Test
  void reportThatCouldNotBeReadSaysTheSentenceOfTheRefusalRatherThanItsWireShape()
      throws IOException {
    replyDraft(root).withKey("ABC-16").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-16");
    doThrow(new IllegalStateException("the round file is not readable"))
        .when(replies)
        .render("a16");

    Page page = openLiveBoard();
    ask(page, "replies a16");

    assertThat(page.locator("#toasts .toast.error")).hasText("the round file is not readable");
  }

  @Test
  void reportAboutTaskThatIsGoneSaysTheBoardIsUpToDateNow() throws IOException {
    replyDraft(root).withKey("ABC-18").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-18");
    doThrow(Refusal.noSuchTask("a18")).when(replies).render("a18");
    Page page = openLiveBoard();

    ask(page, "replies a18");

    assertThat(page.locator("#toasts .toast.error")).containsText("The board is up to date now.");
  }

  @Test
  void roundReadAgainTwiceShowsTheLaterReadWhenTheEarlierOneAnswersLast() throws IOException {
    replyDraft(root).withKey("ABC-19").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-19");
    Page page = openLiveBoard();
    ask(page, "replies a19");
    assertThat(page.locator("#report-body")).containsText("Renamed it.");
    List<Runnable> answers = new java.util.concurrent.CopyOnWriteArrayList<>();
    List<String> bodies = new java.util.concurrent.CopyOnWriteArrayList<>();
    page.route(
        "**/api/commands/replies**",
        route -> {
          String read;
          try (java.io.InputStream in =
              java.net.URI.create(route.request().url()).toURL().openStream()) {
            read = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          bodies.add(read);
          answers.add(() -> route.fulfill(new Route.FulfillOptions().setBody(read)));
        });
    replyDraft(root).withKey("ABC-19").withText("## thread 1\nFIXED - The older read.\n").build();
    state.putTask(
        "ABC-19",
        state
            .task("ABC-19")
            .orElseThrow()
            .withStatus(TaskStatus.REVIEW_PENDING, "older", System.currentTimeMillis()));
    page.waitForCondition(() -> bodies.stream().anyMatch(body -> body.contains("The older read.")));
    replyDraft(root).withKey("ABC-19").withText("## thread 1\nFIXED - The newer read.\n").build();
    state.putTask(
        "ABC-19",
        state
            .task("ABC-19")
            .orElseThrow()
            .withStatus(TaskStatus.REVIEW_PENDING, "newer", System.currentTimeMillis()));
    page.waitForCondition(() -> bodies.stream().anyMatch(body -> body.contains("The newer read.")));
    int newest = answers.size() - 1;
    answers.get(newest).run();
    assertThat(page.locator("#report-body")).containsText("The newer read.");

    page.waitForRequestFinished(() -> answers.subList(0, newest).forEach(Runnable::run));
    page.evaluate("() => new Promise(done => requestAnimationFrame(() => done()))");

    org.assertj.core.api.Assertions.assertThat(page.locator("#report-body").textContent())
        .contains("The newer read.");
  }

  @Test
  void reportAboutOneTaskIsTitledByBothTheNamesItAnswersTo() throws IOException {
    replyDraft(root).withKey("ABC-7").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-7");

    Page page = openLiveBoard();
    ask(page, "replies a7");

    assertThat(page.locator("#report-title")).hasText("replies a7 · ABC-7");
  }

  @Test
  void reportTypedBeforeTheBoardArrivedIsStillTitledByBothNamesOfItsTask() throws IOException {
    replyDraft(root).withKey("ABC-21").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-21");
    List<Route> board = new java.util.concurrent.CopyOnWriteArrayList<>();
    Page page = session.newPage();
    page.route("**/api/tasks", board::add);
    page.navigate("http://localhost:" + port + "/");
    page.waitForCondition(() -> !board.isEmpty());
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    ask(page, "replies a21");
    board.forEach(Route::resume);

    assertThat(page.locator("#report-title")).hasText("replies a21 · ABC-21");
  }

  @Test
  void reportIsNamedToScreenReaderByItsTitle() throws IOException {
    replyDraft(root).withKey("ABC-7").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-7");

    Page page = openLiveBoard();
    ask(page, "replies a7");

    assertThat(
            page.getByRole(
                AriaRole.DIALOG, new Page.GetByRoleOptions().setName("replies a7 · ABC-7")))
        .isVisible();
  }

  @Nested
  class WhileTheStatsReportIsOpenAndTheRepliesAnswerIsHeld {

    Page page;
    List<Route> held;

    @BeforeEach
    void openStatsOverHeldRepliesAnswer() throws IOException {
      replyDraft(root).withKey("ABC-7").withText("## thread 1\nFIXED - The first round.\n").build();
      putReviewTask("ABC-7");
      page = openLiveBoard();
      held = new CopyOnWriteArrayList<>();
      page.route(Pattern.compile("/api/commands/replies\\?about=a7$"), held::add);
      page.waitForRequest(
          Pattern.compile("/api/commands/replies\\?about=a7$"), () -> ask(page, "replies a7"));
      page.locator("#reports-toggle").click();
      page.locator("#show-stats").click();
      assertThat(page.locator("#report-title")).containsText("stats");
    }

    @Test
    void reportOpenedEarlierThatFailsAfterLaterOneOpenedRaisesNoError() {
      page.waitForRequestFinished(
          new Page.WaitForRequestFinishedOptions()
              .setPredicate(request -> request.url().endsWith("about=a7")),
          () ->
              held.get(0)
                  .fulfill(
                      new Route.FulfillOptions()
                          .setStatus(500)
                          .setBody("{\"error\":\"too late\"}")));
      page.evaluate(
          "() => new Promise(done => setTimeout(() => requestAnimationFrame(() => done(true))))");

      assertThat(page.locator("#toasts .toast.error")).hasCount(0);
    }

    @Test
    void reportOpenedLastStaysOnScreenWhenAnEarlierOneAnswersAfterIt() {
      page.waitForRequestFinished(
          new Page.WaitForRequestFinishedOptions()
              .setPredicate(request -> request.url().endsWith("about=a7")),
          () -> held.get(0).resume());
      page.evaluate(
          "() => new Promise(done => setTimeout(() => requestAnimationFrame(() => done(true))))");

      org.assertj.core.api.Assertions.assertThat(page.locator("#report-body").textContent())
          .doesNotContain("The first round.");
    }
  }

  @Test
  void typedReportNarrowsToTheTaskItNamesInsteadOfAnsweringForAllOfThem() throws IOException {
    replyDraft(root).withKey("ABC-2").withText("## thread 1\nFIXED - Renamed it.\n").build();
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.REVIEW_PENDING).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.REVIEW_PENDING).build());

    Page page = openLiveBoard();
    ask(page, "replies a1");

    assertThat(page.locator("#report-body")).containsText("a1 has no drafted replies");
  }

  @Test
  void taskNamedInTheActivityLogOpensFromItsLine() {
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.IN_PROGRESS)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null)))
            .build());
    state.putTask(
        "ABC-2",
        alphaTask("ABC-2", TaskStatus.IN_PROGRESS)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 2_000L, null)))
            .build());

    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-activity").click();
    assertThat(page.locator("#report-body")).containsText("→ agent working");
    page.locator("#report-body a.task-link", new Page.LocatorOptions().setHasText("ABC-2")).click();

    assertThat(page.locator("#report")).isHidden();
    assertThat(page.locator("#pane article .alias")).hasText("a2");
  }

  @Test
  void reportClosesWhenTheDimmedAreaAroundItIsClicked() {
    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-activity").click();
    assertThat(page.locator("#report")).isVisible();

    page.mouse().click(4, 4);

    assertThat(page.locator("#report")).isHidden();
  }

  @Test
  void theShortcutsLeaveTheBoardAloneWhileReportIsOpen() {
    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-activity").click();
    assertThat(page.locator("#report")).isVisible();
    page.keyboard().press("Control+k");
    page.locator("#close-report").click();

    assertThat(page.locator("#palette")).isHidden();
  }

  @Test
  void reportSurvivesSelectionThatStartedInsideItAndEndedOutside() {
    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-activity").click();

    page.locator("#report-body").hover();
    page.mouse().down();
    page.mouse().move(4, 4);
    page.mouse().up();

    assertThat(page.locator("#report")).isVisible();
  }

  @Test
  void helpAlsoSaysWhatTheBoardsOwnMarksMean() {
    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-help").click();

    assertThat(page.locator("#report")).isVisible();
    assertThat(page.locator("#report-section .legend button.again")).hasText("Deploy");
    assertThat(page.locator("#report-section .legend .checks.red")).hasCount(1);
    assertThat(page.locator("#report-section .legend .tabs button.you")).hasText("Drafted review");
    assertThat(page.locator("#report-section .legend a.id")).hasCount(1);
    assertThat(page.locator("#report-section .legend .repos .tick")).hasCount(1);
  }
}
