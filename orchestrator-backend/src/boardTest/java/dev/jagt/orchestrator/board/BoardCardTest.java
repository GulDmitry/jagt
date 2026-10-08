package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class BoardCardTest extends BoardPageContext {

    @Test
    void aCardShowsTheTaskAsTheProjectionDescribesIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1").title("Widget layout is off")
                .mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .alias")).hasText("a1");
        assertThat(page.locator("article .id")).hasText("ABC-1");
        assertThat(page.locator("article .badge")).hasText("you can deploy it");
        assertThat(page.locator("article .title")).hasText("Widget layout is off");
        assertThat(page.locator("article .detail")).hasCount(0);
    }

    @Test
    void aCardSaysHowLongTheReviewRequestHasBeenOpen() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .requestOpenedAt(System.currentTimeMillis() - java.time.Duration.ofHours(8).toMillis())
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .mr-age")).hasText("MR 8h");
    }

    @Test
    void offersTheRequestUnagedWhileNoReadHasSaidWhenItWasOpened() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .mr-age")).hasText("MR");
    }

    @Test
    void opensTheReviewRequestFromTheAgeItIsWearing() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article a.mr-age")).hasAttribute("href", "https://host.example/mr/7");
    }

    @Test
    void namesEachRequestByProjectAndAgesNoneWhenATaskSpansRepositories() {
        state.putTask("ABC-1", TaskState.builder(List.of(
                        new dev.jagt.orchestrator.task.TaskRepo("alpha",
                                root.resolve("ABC-1-alpha").toString(), null,
                                "https://host.example/alpha/mr/7", null),
                        new dev.jagt.orchestrator.task.TaskRepo("beta",
                                root.resolve("ABC-1-beta").toString(), null,
                                "https://host.example/beta/mr/3", null)),
                TaskStatus.CI_POLLING).alias("a1")
                .requestOpenedAt(System.currentTimeMillis() - java.time.Duration.ofHours(8).toMillis())
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .mr-age")).hasText(new String[]{"alpha", "beta"});
    }

    @Test
    void aTaskSpanningRepositoriesNamesEachOnceWithItsRequestOnTheName() {
        state.putTask("ABC-1", TaskState.builder(List.of(
                        TaskRepo.of("alpha", root.resolve("ABC-1-alpha").toString())
                                .withMrUrl("https://host.example/alpha/mr/7"),
                        TaskRepo.of("beta", root.resolve("ABC-1-beta").toString())
                                .withMrUrl("https://host.example/beta/mr/7")),
                        TaskStatus.CI_POLLING).alias("a1").lastActiveTimestamp(System.currentTimeMillis())
                .pipelineStatus("failed").approved(true).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta > *")).hasCount(3);
        assertThat(page.locator("article .meta .repos")).hasText("alpha + beta \u2713");
    }

    @Test
    void showsTheStatusInWordsWithItsOwnAgeInsideTheSameChip() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .status")).hasText("out for review 0m");
        assertThat(page.locator("article .status .age")).hasText("0m");
    }

    @Test
    void saysNothingUnderACardAboutARequestItAlreadyLinksTo() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("running").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .detail")).hasCount(0);
    }

    @Test
    void aPollThatStoppedRingsTheRequestRatherThanTakingAFifthPlaceInTheRow() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(System.currentTimeMillis() - java.time.Duration.ofDays(9).toMillis())
                .pipelineStatus("failed")
                .lastPolledAt(System.currentTimeMillis()).lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("beta", root.resolve("ABC-2-beta").toString(),
                TaskStatus.IN_PROGRESS).alias("b2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        Locator meta = page.locator("article", new Page.LocatorOptions().setHasText("a1")).locator(".meta");
        assertThat(meta.locator("a.mr-age.stalled")).hasCount(1);
        assertThat(meta.locator("> *")).hasCount(4);
    }

    @Test
    void aRequestNothingPollsAnyMoreSaysWhyInItsHover() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(System.currentTimeMillis() - java.time.Duration.ofDays(9).toMillis())
                .lastPolledAt(System.currentTimeMillis()).lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article a.mr-age.stalled")).hasAttribute("data-tip",
                Pattern.compile("polling stopped — no further polls: this round is past its 24h window"));
    }

    @Test
    void opensTheTicketFromTheTaskNumber() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").ticketUrl("https://tracker.example/ABC-1")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article a.id")).hasAttribute("href", "https://tracker.example/ABC-1");
    }

    @Test
    void showsTheTaskNumberAsPlainTextWhenNoTrackerGaveItAUrl() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article a.id")).hasCount(0);
    }

    @Test
    void answersWhoseMoveItIsOnTheCardThatOffersNoButtonToPress() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.REVIEW_PENDING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(System.currentTimeMillis()).lastPolledAt(System.currentTimeMillis())
                .message("no changes: already handled").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .badge")).hasCount(0);
        assertThat(page.locator("article .hint")).hasCount(0);
        assertThat(page.locator("article .detail")).hasText(
                "ANSWERED: already handled — the open threads are the reviewer's to close");
    }

    @Test
    void aCardColoursItsLineByTheKindTheServerNamesNotByItsWords() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_FAILED).alias("a1").message("lint red").lastActiveTimestamp(System.currentTimeMillis())
                        .build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.DEPLOY_CONFLICT).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .detail.problem")).hasText("PROBLEM: lint red");
        assertThat(page.locator("article .detail.you")).containsText("NEEDS YOU");
    }

    @Test
    void doesNotShoutAboutAQuestionWhileAPollIsStillReadingTheRoundItWasAskedOn() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.REVIEW_PENDING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(System.currentTimeMillis()).lastPolledAt(System.currentTimeMillis())
                .message("awaiting: which lock do we take")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .detail.problem")).hasCount(0);
        assertThat(page.locator("#waiting")).isHidden();
    }

    @Test
    void interruptsForABlockedTaskAndOnlyOffersTheNextMoveForOneThatCanWait() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_FAILED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                        TaskStatus.APPROVED).alias("a2").mrUrl("https://host.example/mr/8")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .badge"))
                .hasText(new String[]{"relay the failed checks", "you can deploy it"});
        assertThat(page.locator("#waiting")).hasText("1 need your action");
        page.locator("#mine").check();
        assertThat(page.locator("article .alias")).hasText(new String[]{"a1"});
    }

    @Test
    void asksForNothingOnACardWhoseChangeIsAlreadyDeployed() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .badge")).hasCount(0);
        assertThat(page.locator("#waiting")).isHidden();
    }

    @Test
    void aCardStaysInsideAWindowTooNarrowForItsBadgeAndItsTitleAtOnce() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_FAILED).alias("a1").mrUrl("https://host.example/mr/7")
                .title("Widget layout is off on every window narrower than a laptop screen")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.setViewportSize(400, 1200);

        assertThat(page.locator("article"))
                .isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
    }

    @Test
    void aTitleWithNoPlaceToBreakStillStaysInsideTheCard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").title("w".repeat(150))
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        Object spills = page.locator("article .title").evaluate("title => title.scrollWidth > title.clientWidth");

        org.assertj.core.api.Assertions.assertThat(spills).isEqualTo(false);
    }

    @Test
    void aBadgeOpensItsHintFromTheKeyboard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.REVIEWED).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        page.locator("article .badge").focus();

        assertThat(page.locator("article .badge")).isFocused();
        assertThat(page.locator("#tip")).isVisible();
    }

    @Test
    void aTitleCutShortOpensItsWholeTextFromTheKeyboard() {
        String pasted = "Widget layout is off. ".repeat(8) + "The end of the paragraph.";
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").title(pasted).lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        page.locator("article .title").focus();

        assertThat(page.locator("article .title")).isFocused();
        assertThat(page.locator("article .title")).hasAttribute("aria-label", pasted);
        assertThat(page.locator("#tip")).containsText("The end of the paragraph.");
    }

    @Test
    void showsAHandEditedAliasAsTextRatherThanAsMarkup() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1<b>x</b>").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .alias")).hasText("a1<b>x</b>");
        assertThat(page.locator("article .alias b")).hasCount(0);
    }

    @Test
    void aCardGroupsWhatMovesTheTaskOnAwayFromWhatOnlyLooksAtItAndMarksTheObviousOne() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1")
                .mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .actions button")).hasText(
                new String[]{"Check review", "Deploy", "Done", "Focus", "Open IDE", "Diff", "Restart agent"});
        assertThat(page.locator("article .actions.flow button")).hasText(
                new String[]{"Check review", "Deploy", "Done"});
        assertThat(page.locator("article .actions.tool button")).hasText(
                new String[]{"Focus", "Open IDE", "Diff", "Restart agent"});
        assertThat(page.locator("article .actions button.primary")).hasText("Deploy");
    }

    @Test
    void theObviousActionKeepsItsLabelReadableEvenWhenItSitsInTheQuietRow() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .actions.tool button.primary")).hasText("Focus");
        assertThat(page.locator("article .actions.tool button.primary")).hasCSS("color", "rgb(255, 255, 255)");
    }

    @Test
    void hoveringTheRevertButtonShowsThatItTakesOnlyTheLastDeployBackOut() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Revert").setExact(true)).hover();

        assertThat(page.locator("#tip")).isVisible();
        assertThat(page.locator("#tip"))
                .hasText("revert the last deploy's merge commit and push; earlier deploys stay live");
    }

    @Test
    void keepsALongLivedTasksTimelineInsideAScrollingTipInsteadOfCoveringTheBoard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis())
                .history(java.util.Collections.nCopies(40,
                        new dev.jagt.orchestrator.task.StatusChange(TaskStatus.IN_PROGRESS, System
                                .currentTimeMillis(), null)))
                .build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .status").hover();

        assertThat(page.locator("#tip")).isVisible();
        assertThat(page.locator("#tip")).hasClass("scrolls");
    }

    @Test
    void aStatusThatIsNotLiveStillOpensItsTimelineFromTheKeyboard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .status").focus();

        assertThat(page.locator("#tip")).containsText("IN_PROGRESS");
    }

    @Test
    void aTooltipGoesAwayWithThePointerThatOpenedIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).hover();
        page.locator("h1").hover();

        assertThat(page.locator("#tip")).isHidden();
    }

    @Test
    void aCardShowsTheChecksAsRedAndCarriesWhatTheHostSaidAboutThem() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("failed").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta .checks.red")).hasCount(1);
        assertThat(page.locator("article .meta .checks.red"))
                .hasAttribute("data-tip", Pattern.compile("checks: failed"));
    }

    @Test
    void aMarkWithoutWordsIsNamedToAScreenReaderAndReachableByKeyboard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("failed").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        Locator checks = page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName("checks: failed"));
        checks.focus();

        assertThat(checks).isFocused();
        assertThat(page.locator("#tip")).hasText("checks: failed");
    }

    @Test
    void aCardIsNamedToAScreenReaderByBothTheNamesItAnswersTo() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.getByRole(AriaRole.ARTICLE, new Page.GetByRoleOptions().setName("a1 \u00b7 ABC-1")))
                .hasCount(1);
    }

    @Test
    void aRunStillGoingPulsesBesideTheRequestInsteadOfColouringIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(System.currentTimeMillis()).pipelineStatus("running").lastActiveTimestamp(System
                .currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta .checks.running")).hasCount(1);
        assertThat(page.locator("article .meta a.mr-age")).hasClass("mr-age");
    }

    @Test
    void theRequestWearsATickOnceSomebodyHasApprovedIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .approved(false).lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                        TaskStatus.REVIEWED).alias("a2").mrUrl("https://host.example/mr/8")
                .approved(true).lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article").nth(0).locator(".meta a.mr-age")).hasText("MR");
        assertThat(page.locator("article").nth(0).locator(".meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("not approved yet"));
        assertThat(page.locator("article").nth(1).locator(".meta a.mr-age.approved")).hasText("MR \u2713");
    }

    @Test
    void aRedRunDoesNotSwallowTheApprovalItLandedOn() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("failed").approved(true).lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta a.mr-age.approved")).hasText("MR \u2713");
        assertThat(page.locator("article .meta .checks.red")).hasCount(1);
    }

    @Test
    void checksThatPassedWearTheirOwnDotAndLeaveTheRequestPlain() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(System.currentTimeMillis()).pipelineStatus("success").approved(false)
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta .checks.green"))
                .hasAttribute("data-tip", Pattern.compile("checks: success"));
        assertThat(page.locator("article .meta a.mr-age")).hasClass("mr-age");
    }

    @Test
    void aVerdictNothingCanReadWearsNoDotAndKeepsTheHostsWordOnTheRequest() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("completed").lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta .checks")).hasCount(0);
        assertThat(page.locator("article .meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("checks: completed"));
    }

    @Test
    void aRequestNoReadHasSeenYetWearsNoVerdictAndSaysSoOnHover() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(System.currentTimeMillis()).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta .checks")).hasCount(0);
        assertThat(page.locator("article .meta a.mr-age")).hasText("MR");
        assertThat(page.locator("article .meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("checks: nothing has read them yet"));
    }

    @Test
    void aTaskSpanningRepositoriesWearsOneTickForAllOfThem() {
        state.putTask("ABC-1", TaskState.builder(List.of(
                        TaskRepo.of("alpha", root.resolve("ABC-1-alpha").toString())
                                .withMrUrl("https://host.example/alpha/mr/7"),
                        TaskRepo.of("beta", root.resolve("ABC-1-beta").toString())
                                .withMrUrl("https://host.example/beta/mr/7")),
                        TaskStatus.CI_POLLING).alias("a1").lastActiveTimestamp(System.currentTimeMillis())
                .pipelineStatus("failed").approved(true).build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article .meta a.mr-age")).hasCount(2);
        assertThat(page.locator("article .meta .tick")).hasCount(1);
        assertThat(page.locator("article .meta .checks.red")).hasCount(1);
    }

    @Test
    void theDeployVerbIsColouredWhileItsLastRunIsStillLive() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").lastActiveTimestamp(System.currentTimeMillis())
                .mrUrl("https://host.example/mr/7").deployCommit("abc1234").build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article button.again")).hasText("Deploy");
        assertThat(page.locator("article .meta .status.live")).hasCount(0);
    }

    @Test
    void theDeployVerbKeepsItsFillAndTakesTheMarkAsARingWhileItIsTheHighlightedMove() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1").lastActiveTimestamp(System.currentTimeMillis())
                .mrUrl("https://host.example/mr/7").deployCommit("abc1234").build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article button.primary.again")).hasText("Deploy");
    }

    @Test
    void theStateSaysTheWorkIsLiveWhereNoVerbOnTheCardIsTheDeploy() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis())
                .deployCommit("abc1234").build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article button.again")).hasCount(0);
        assertThat(page.locator("article .meta .status.live")).hasCount(1);
    }

    @Test
    void theDeployVerbIsPlainWhileNothingOfTheTaskIsLive() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1").lastActiveTimestamp(System.currentTimeMillis())
                .mrUrl("https://host.example/mr/7").build());

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        assertThat(page.locator("article button[data-action=deploy]")).isVisible();
        assertThat(page.locator("article button.again")).hasCount(0);
    }
}
