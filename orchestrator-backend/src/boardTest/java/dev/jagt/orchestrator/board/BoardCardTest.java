package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BoardCardTest extends BoardPageContext {

  @Test
  void cardShowsTheTaskAsTheProjectionDescribesIt() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.APPROVED)
            .alias("a1")
            .title("Widget layout is off")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .alias")).hasText("a1");
    assertThat(page.locator("article .id")).hasText("ABC-1");
    assertThat(page.locator("article .badge")).hasText("you can deploy it");
    assertThat(page.locator("article .title")).hasText("Widget layout is off");
    assertThat(page.locator("article .detail")).hasCount(0);
  }

  @Test
  void flaggedCardStaysFlaggedAfterTheBoardIsReloaded() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .title("Widget layout is off")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    page.locator("article button.flag").click();
    page.reload();

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    assertThat(page.locator("article button.flag")).hasAttribute("aria-pressed", "true");
  }

  @Test
  void cardSaysHowLongTheReviewRequestHasBeenOpen() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .requestOpenedAt(System.currentTimeMillis() - java.time.Duration.ofHours(8).toMillis())
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .mr-age")).hasText("request 8h");
  }

  @Test
  void offersTheRequestUnagedWhileNoReadHasSaidWhenItWasOpened() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .mr-age")).hasText("request");
  }

  @Test
  void opensTheReviewRequestFromTheAgeItIsWearing() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article a.mr-age")).hasAttribute("href", "https://host.example/mr/7");
  }

  @Test
  void opensTheRequestFromItsProjectsNameWhereTheBoardNamesProjects() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .requestOpenedAt(System.currentTimeMillis() - java.time.Duration.ofHours(8).toMillis())
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    state.putTask(
        "ABC-2",
        TaskState.builder("beta", root.resolve("ABC-2-beta").toString(), TaskStatus.IN_PROGRESS)
            .alias("b2")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");
    page.locator("#tasks .row:has-text(\"a1\")").click();

    assertThat(page.locator("#pane article a.mr-age")).hasText("alpha 8h");
  }

  @Test
  void namesEachRequestByProjectAndAgesNoneWhenTaskSpansRepositories() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(
                    new TaskRepo(
                        "alpha",
                        root.resolve("ABC-1-alpha").toString(),
                        null,
                        "https://host.example/alpha/mr/7"),
                    new TaskRepo(
                        "beta",
                        root.resolve("ABC-1-beta").toString(),
                        null,
                        "https://host.example/beta/mr/3")),
                TaskStatus.CI_POLLING)
            .alias("a1")
            .requestOpenedAt(System.currentTimeMillis() - java.time.Duration.ofHours(8).toMillis())
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .mr-age")).hasText(new String[] {"alpha", "beta"});
  }

  @Test
  void taskSpanningRepositoriesNamesEachOnceWithItsRequestOnTheName() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(
                    TaskRepo.of("alpha", root.resolve("ABC-1-alpha").toString())
                        .withMrUrl("https://host.example/alpha/mr/7"),
                    TaskRepo.of("beta", root.resolve("ABC-1-beta").toString())
                        .withMrUrl("https://host.example/beta/mr/7")),
                TaskStatus.CI_POLLING)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .pipelineStatus("failed")
            .approved(true)
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta > *")).hasCount(3);
    assertThat(page.locator("article .meta .repos")).hasText("alpha + beta ✓");
  }

  @Test
  void showsTheStatusInWordsWithItsOwnAgeInsideTheSameChip() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .status")).hasText("out for review 0m");
    assertThat(page.locator("article .status .age")).hasText("0m");
  }

  @Test
  void saysNothingUnderCardAboutRequestItAlreadyLinksTo() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .pipelineStatus("running")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .detail")).hasCount(0);
  }

  @Test
  void pollThatStoppedRingsTheRequestRatherThanTakingFifthPlaceInTheRow() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .mrCreatedAt(System.currentTimeMillis() - java.time.Duration.ofDays(9).toMillis())
            .pipelineStatus("failed")
            .lastPolledAt(System.currentTimeMillis())
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    state.putTask(
        "ABC-2",
        TaskState.builder("beta", root.resolve("ABC-2-beta").toString(), TaskStatus.IN_PROGRESS)
            .alias("b2")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    Locator meta =
        page.locator("article", new Page.LocatorOptions().setHasText("a1")).locator(".meta");
    assertThat(meta.locator("a.mr-age.stalled")).hasCount(1);
    assertThat(meta.locator("> *")).hasCount(3);
  }

  @Test
  void requestNothingPollsAnyMoreSaysWhyInItsHover() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .mrCreatedAt(System.currentTimeMillis() - java.time.Duration.ofDays(9).toMillis())
            .lastPolledAt(System.currentTimeMillis())
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article a.mr-age.stalled"))
        .hasAttribute(
            "data-tip",
            Pattern.compile(
                "polling stopped — no further polls: this round is past its 24h window"));
  }

  @Test
  void opensTheTicketFromTheTaskNumber() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .ticketUrl("https://tracker.example/ABC-1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article a.id")).hasAttribute("href", "https://tracker.example/ABC-1");
  }

  @Test
  void showsTheTaskNumberAsPlainTextWhenNoTrackerGaveItUrl() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article a.id")).hasCount(0);
  }

  @Test
  void answersWhoseMoveItIsOnTheCardThatOffersNoButtonToPress() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                "alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .mrCreatedAt(System.currentTimeMillis())
            .lastPolledAt(System.currentTimeMillis())
            .message("already handled")
            .report(AgentReport.NO_CHANGES)
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .badge")).hasCount(0);
    assertThat(page.locator("article .hint")).hasCount(0);
    assertThat(page.locator("article .detail"))
        .hasText("ANSWERED: already handled — the open threads are the reviewer's to close");
  }

  @Test
  void cardColoursItsLineByTheKindTheServerNamesNotByItsWords() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_FAILED)
            .alias("a1")
            .message("lint red")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    state.putTask(
        "ABC-2",
        TaskState.builder(
                "alpha", root.resolve("ABC-2-alpha").toString(), TaskStatus.DEPLOY_CONFLICT)
            .alias("a2")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .detail.problem")).hasText("PROBLEM: lint red");
    page.locator("#tasks .row:has-text(\"a2\") .pick").click();
    assertThat(page.locator("article .detail.you")).containsText("NEEDS YOU");
  }

  @Test
  void doesNotShoutAboutQuestionWhilePollIsStillReadingTheRoundItWasAskedOn() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                "alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .mrCreatedAt(System.currentTimeMillis())
            .lastPolledAt(System.currentTimeMillis())
            .message("which lock do we take")
            .report(AgentReport.QUESTION)
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .detail.problem")).hasCount(0);
    assertThat(page.locator("#waiting")).isHidden();
  }

  @Test
  void interruptsForBlockedTaskAndOnlyOffersTheNextMoveForOneThatCanWait() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_FAILED)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    state.putTask(
        "ABC-2",
        TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(), TaskStatus.APPROVED)
            .alias("a2")
            .mrUrl("https://host.example/mr/8")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("#tasks .row .badge"))
        .hasText(new String[] {"relay the failed checks", "you can deploy it"});
    assertThat(page.locator("#waiting")).hasText("1 need your action");
    assertThat(page.locator("#tasks .group.yours .alias")).hasText(new String[] {"a1"});
  }

  @Test
  void asksForNothingOnCardWhoseChangeIsAlreadyDeployed() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.DEPLOYED)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .badge")).hasCount(0);
    assertThat(page.locator("#waiting")).isHidden();
  }

  @Test
  void cardStaysInsideWindowTooNarrowForItsBadgeAndItsTitleAtOnce() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_FAILED)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .title("Widget layout is off on every window narrower than a laptop screen")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    page.setViewportSize(400, 1200);
    page.locator("#tasks .row .pick").click();

    assertThat(page.locator("article"))
        .isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
  }

  @Test
  void phoneShowsTheOpenTaskWithItsVerbsAndSayFormAndNoSidewaysScroll() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_FAILED)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .title("Widget layout is off on every window narrower than a laptop screen")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();
    page.setViewportSize(390, 844);
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    Object listSpills =
        page.evaluate(
            "document.documentElement.scrollWidth > document.documentElement.clientWidth");

    page.locator("#tasks .row .pick").click();
    page.locator("#pane .tabs button[data-tab=session]").click();
    Object paneSpills =
        page.evaluate(
            "document.documentElement.scrollWidth > document.documentElement.clientWidth");

    Object pastTheEdge =
        page.evaluate(
            "[...document.querySelectorAll('body *')].filter(e => e.getBoundingClientRect().width >"
                + " 0 && e.getBoundingClientRect().right > innerWidth + 1 && !e.closest('dialog,"
                + " .screen, .doc')).map(e => e.tagName + '.' + e.className + '#' + e.id)");

    org.assertj.core.api.Assertions.assertThat(listSpills).isEqualTo(false);
    org.assertj.core.api.Assertions.assertThat(paneSpills).isEqualTo(false);
    org.assertj.core.api.Assertions.assertThat(pastTheEdge).isEqualTo(java.util.List.of());
    org.assertj.core.api.Assertions.assertThat(
            page.locator("#pane .tabs button").first().boundingBox().height)
        .isLessThan(40);
    assertThat(page.locator("#pane article .actions button").first())
        .isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
    assertThat(page.locator("#pane .say input"))
        .isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
    assertThat(page.locator("#pane .say button"))
        .isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
  }

  @Test
  void phoneShowsTheWholeReportsMenu() {
    Page page = session.newPage();
    page.setViewportSize(390, 844);
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    page.locator("#reports-toggle").click();

    assertThat(page.locator("#reports"))
        .isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
  }

  @Test
  void phoneGivesTheNewTaskBaseBranchFieldRoomToType() {
    Page page = session.newPage();
    page.setViewportSize(390, 844);
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    page.locator("#new-task").click();

    org.assertj.core.api.Assertions.assertThat(page.locator("#base-branch").boundingBox().width)
        .isGreaterThan(200);
  }

  @Test
  void titleWithNoPlaceToBreakStillStaysInsideTheCard() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .title("w".repeat(150))
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    Object spills =
        page.locator("article .title").evaluate("title => title.scrollWidth > title.clientWidth");

    org.assertj.core.api.Assertions.assertThat(spills).isEqualTo(false);
  }

  @Test
  void badgeOpensItsHintFromTheKeyboard() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.REVIEWED)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    page.locator("article .badge").focus();

    assertThat(page.locator("article .badge")).isFocused();
    assertThat(page.locator("#tip")).isVisible();
  }

  @Test
  void pollThatStoppedOnRequestWithNoLinkRingsTheStatus() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("!7")
            .mrCreatedAt(System.currentTimeMillis() - java.time.Duration.ofDays(9).toMillis())
            .lastPolledAt(System.currentTimeMillis())
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .status.stalled"))
        .hasAttribute(
            "data-tip",
            Pattern.compile(
                "polling stopped — no further polls: this round is past its 24h window"));
  }

  @Test
  void showsHandEditedAliasAsTextRatherThanAsMarkup() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1<b>x</b>")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .alias")).hasText("a1<b>x</b>");
    assertThat(page.locator("article .alias b")).hasCount(0);
  }

  @Test
  void cardGroupsWhatMovesTheTaskOnAwayFromWhatOnlyLooksAtItAndMarksTheObviousOne() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.APPROVED)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .actions button"))
        .hasText(
            new String[] {
              "Check review",
              "Deploy",
              "Pause",
              "Done",
              "Focus",
              "Open IDE",
              "Diff",
              "Restart agent"
            });
    assertThat(page.locator("article .actions.flow button"))
        .hasText(new String[] {"Check review", "Deploy", "Pause", "Done"});
    assertThat(page.locator("article .actions.tool button"))
        .hasText(new String[] {"Focus", "Open IDE", "Diff", "Restart agent"});
    assertThat(page.locator("article .actions button.primary")).hasText("Deploy");
  }

  @Test
  void theObviousActionKeepsItsLabelReadableEvenWhenItSitsInTheQuietRow() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.REVERTED)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .actions.tool button.primary")).hasText("Focus");
    assertThat(page.locator("article .actions.tool button.primary"))
        .hasCSS("color", "rgb(255, 255, 255)");
  }

  @Test
  void noVerbIsFilledWhileTheAgentHoldsTheMove() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article button[data-action=focus]"))
        .not()
        .hasClass(Pattern.compile("\\bprimary\\b"));
  }

  @Test
  void doneStaysMutedAtTheRowsEndEvenWhereItIsTheMove() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.APPROVED)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article button.apart"))
        .not()
        .hasClass(Pattern.compile("\\bprimary\\b"));
  }

  @Test
  void hoveringTheRevertButtonShowsThatItTakesOnlyTheLastDeployBackOut() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.DEPLOYED)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Revert").setExact(true))
        .hover();

    assertThat(page.locator("#tip")).isVisible();
    assertThat(page.locator("#tip"))
        .hasText("revert the last deploy's merge commit and push; earlier deploys stay live");
  }

  @Test
  void keepsLongTipInsideScrollingTipInsteadOfCoveringTheBoard() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .mrCreatedAt(System.currentTimeMillis() - java.time.Duration.ofDays(9).toMillis())
            .lastPolledAt(System.currentTimeMillis())
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();
    page.setViewportSize(1300, 170);

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    assertThat(page.locator("article .mr-age")).isVisible();
    var chip = page.locator("article .mr-age").boundingBox();
    page.mouse().move(chip.x + 4, chip.y + 4);

    assertThat(page.locator("#tip")).hasClass("scrolls");
  }

  @Test
  void statusChipCarriesNoHistoryInItsHover() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .status"))
        .not()
        .hasAttribute("data-tip", Pattern.compile("IN_PROGRESS|_"));
  }

  @Test
  void liveStatusOpensItsHoverFromTheKeyboard() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge("abc1234")), 1, null))
            .build());
    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    page.locator("article .status").focus();

    assertThat(page.locator("#tip")).containsText("its work is on a shared branch");
  }

  @Test
  void tipBeingReadStaysOpenInItsNewWordsWhenRepaintRebuildsItsCard() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.APPROVED)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    page.locator("article .badge").focus();
    assertThat(page.locator("#tip")).containsText("approved: deploy or done");

    state.putTask(
        "ABC-1",
        state
            .task("ABC-1")
            .orElseThrow()
            .withStatus(TaskStatus.DEPLOY_CONFLICT, "conflict", System.currentTimeMillis()));

    assertThat(page.locator("#tip")).containsText("resolve the conflict");
  }

  @Test
  void tooltipGoesAwayWithThePointerThatOpenedIt() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true))
        .hover();
    page.locator("h1").hover();

    assertThat(page.locator("#tip")).isHidden();
  }

  @Test
  void cardShowsTheChecksAsRedAndCarriesWhatTheHostSaidAboutThem() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .pipelineStatus("failed")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta .checks.red")).hasCount(1);
    assertThat(page.locator("article .meta .checks.red"))
        .hasAttribute("data-tip", Pattern.compile("checks: failed"));
  }

  @Test
  void markWithoutWordsIsNamedToScreenReaderAndReachableByKeyboard() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .pipelineStatus("failed")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    Locator checks =
        page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName("checks: failed"));
    checks.focus();

    assertThat(checks).isFocused();
    assertThat(page.locator("#tip")).hasText("checks: failed");
  }

  @Test
  void cardIsNamedToScreenReaderByBothTheNamesItAnswersTo() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(
            page.getByRole(
                AriaRole.ARTICLE, new Page.GetByRoleOptions().setName("a1 · ABC-1")))
        .hasCount(1);
  }

  @Test
  void runStillGoingPulsesBesideTheRequestInsteadOfColouringIt() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .mrCreatedAt(System.currentTimeMillis())
            .pipelineStatus("running")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta .checks.running")).hasCount(1);
    assertThat(page.locator("article .meta a.mr-age")).hasClass("mr-age");
  }

  @Test
  void theRequestWearsTickOnceSomebodyHasApprovedIt() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .approved(false)
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    state.putTask(
        "ABC-2",
        TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(), TaskStatus.REVIEWED)
            .alias("a2")
            .mrUrl("https://host.example/mr/8")
            .approved(true)
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta a.mr-age")).hasText("request");
    assertThat(page.locator("article .meta a.mr-age"))
        .hasAttribute("data-tip", Pattern.compile("not approved yet"));
    page.locator("#tasks .row:has-text(\"a2\") .pick").click();
    assertThat(page.locator("article .meta a.mr-age.approved")).hasText("request ✓");
  }

  @Test
  void redRunDoesNotSwallowTheApprovalItLandedOn() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .pipelineStatus("failed")
            .approved(true)
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta a.mr-age.approved")).hasText("request ✓");
    assertThat(page.locator("article .meta .checks.red")).hasCount(1);
  }

  @Test
  void checksThatPassedWearTheirOwnDotAndLeaveTheRequestPlain() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .mrCreatedAt(System.currentTimeMillis())
            .pipelineStatus("success")
            .approved(false)
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta .checks.green"))
        .hasAttribute("data-tip", Pattern.compile("checks: success"));
    assertThat(page.locator("article .meta a.mr-age")).hasClass("mr-age");
  }

  @Test
  void verdictNothingCanReadWearsNoDotAndKeepsTheHostsWordOnTheRequest() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .pipelineStatus("completed")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta .checks")).hasCount(0);
    assertThat(page.locator("article .meta a.mr-age"))
        .hasAttribute("data-tip", Pattern.compile("checks: completed"));
  }

  @Test
  void requestNoReadHasSeenYetWearsNoVerdictAndSaysSoOnHover() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta .checks")).hasCount(0);
    assertThat(page.locator("article .meta a.mr-age")).hasText("request");
    assertThat(page.locator("article .meta a.mr-age"))
        .hasAttribute("data-tip", Pattern.compile("checks: nothing has read them yet"));
  }

  @Test
  void taskSpanningRepositoriesWearsOneTickForAllOfThem() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(
                    TaskRepo.of("alpha", root.resolve("ABC-1-alpha").toString())
                        .withMrUrl("https://host.example/alpha/mr/7"),
                    TaskRepo.of("beta", root.resolve("ABC-1-beta").toString())
                        .withMrUrl("https://host.example/beta/mr/7")),
                TaskStatus.CI_POLLING)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .pipelineStatus("failed")
            .approved(true)
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .meta a.mr-age")).hasCount(2);
    assertThat(page.locator("article .meta .tick")).hasCount(1);
    assertThat(page.locator("article .meta .checks.red")).hasCount(1);
  }

  @Test
  void theDeployVerbIsColouredWhileItsLastRunIsStillLive() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.DEPLOYED)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .mrUrl("https://host.example/mr/7")
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge("abc1234")), 1, null))
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article button.again")).hasText("Deploy");
    assertThat(page.locator("article .meta .status.live")).hasCount(0);
  }

  @Test
  void theDeployVerbKeepsItsFillAndTakesTheMarkAsRingWhileItIsTheHighlightedMove() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.APPROVED)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .mrUrl("https://host.example/mr/7")
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge("abc1234")), 1, null))
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article button.primary.again")).hasText("Deploy");
  }

  @Test
  void theStateSaysTheWorkIsLiveWhereNoVerbOnTheCardIsTheDeploy() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge("abc1234")), 1, null))
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article button.again")).hasCount(0);
    assertThat(page.locator("article .meta .status.live")).hasCount(1);
  }

  @Test
  void theDeployVerbIsPlainWhileNothingOfTheTaskIsLive() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.APPROVED)
            .alias("a1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .mrUrl("https://host.example/mr/7")
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article button[data-action=deploy]")).isVisible();
    assertThat(page.locator("article button.again")).hasCount(0);
  }

  @Test
  void cardsHoverSaysWhatTheSessionJudgedOnEachSwitch() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .title("Widget layout is off")
            .switches(java.util.Map.of("visualize", "no — config-only change"))
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());
    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("article .title"))
        .hasAttribute("data-tip", "visualize: no — config-only change");
  }

  @Test
  void stoppedCardAsksToLiftTheStopWithItsReasonAndHoldsTheEvidenceInTheSameHover() {
    state.putTask(
        "ABC-42",
        TaskState.builder("alpha", root.resolve("ABC-42-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .stop(
                new dev.jagt.orchestrator.task.TaskStop(
                    dev.jagt.orchestrator.task.TaskStop.Trigger.SHIPS,
                    1_700_000_000_000L,
                    "a ship asked past 3 without a new note"))
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    assertThat(page.locator("article .badge"))
        .hasText("lift the stop — ships with no new note");
    assertThat(page.locator("article .badge"))
        .hasAttribute(
            "data-tip",
            Pattern.compile(
                "a ship asked past 3 without a new note\\n"
                    + "Check a new note or commit waits to ship; if one does, report a jagt"
                    + " defect\\n"
                    + "stopped 14 Nov 2023, 22:13 UTC"));
  }

  @Test
  void triagedStopCarriesItsReadAsTheLastLineOfTheHover() {
    state.putTask(
        "ABC-42",
        TaskState.builder("alpha", root.resolve("ABC-42-alpha").toString(), TaskStatus.CI_POLLING)
            .alias("a1")
            .mrUrl("https://host.example/mr/7")
            .stop(
                new dev.jagt.orchestrator.task.TaskStop(
                        dev.jagt.orchestrator.task.TaskStop.Trigger.SHIPS,
                        1_700_000_000_000L,
                        "a ship asked past 3 without a new note")
                    .withTriage(
                        new dev.jagt.orchestrator.task.StopTriage(
                            dev.jagt.orchestrator.task.StopTriage.Who.JAGT,
                            "ships counted past a merge",
                            "H",
                            dev.jagt.orchestrator.task.StopTriage.Lift.SAFE,
                            "request merged",
                            "")))
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("article .badge"))
        .hasAttribute(
            "data-tip",
            Pattern.compile(
                "stopped 14 Nov 2023, 22:13 UTC\\n"
                    + "triage reads: jagt — ships counted past a merge \\(H\\), lift looks"
                    + " safe"));
  }
}
