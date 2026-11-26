package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardHeaderAndFiltersTest extends BoardPageContext {

  final Jobs jobs;

  final Reach reach;

  @Autowired
  BoardHeaderAndFiltersTest(BreakableJob breakable, StateService state, Jobs jobs, Reach reach) {
    super(breakable, state);
    this.jobs = jobs;
    this.reach = reach;
  }

  @Test
  void pollRunningAsItShouldIsNotNewsWhileItsNextLookStaysOnTheRequest() {
    long shipped = System.currentTimeMillis();
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.CI_POLLING)
            .lastActiveTimestamp(shipped)
            .mrUrl("https://host/alpha/-/merge_requests/1")
            .mrCreatedAt(shipped)
            .lastPolledAt(shipped)
            .build());

    Page page = openLiveBoard();

    assertThat(page.locator("#auto-review")).isHidden();
    assertThat(page.locator("article .meta a.mr-age"))
        .hasAttribute("data-tip", Pattern.compile("next poll 10m"));
  }

  @Test
  void theDarkThemeChosenOnTheGearSurvivesReload() {
    Page page = openBoard();

    page.click("#gear");
    page.check("#gear-dialog input[name=theme][value=dark]");
    page.reload();

    assertThat(page.locator("html")).hasAttribute("data-theme", "dark");
  }

  @Test
  void theGearOffersNoLogOutWhileTheBoardSitsBehindNoLogin() {
    Page page = openBoard();

    page.click("#gear");

    assertThat(page.locator("#gear-dialog")).isVisible();
    assertThat(page.locator("#gear-logout")).isHidden();
  }

  @Test
  void theGearOpensTheDialogWithAppearanceLanguageAndConfigurationEachUnderItsHeading() {
    Page page = openBoard();

    page.click("#gear");

    assertThat(page.locator("#gear-dialog")).isVisible();
    assertThat(page.locator("#gear-dialog h3"))
        .hasText(new String[] {"Appearance", "Language", "Configuration"});
  }

  @Test
  void darkAndPolishChosenOnTheGearReadAsChosenAfterReloadAndShowTheirChoiceChecked() {
    Page page = openBoard();
    page.click("#gear");
    page.check("#gear-dialog input[name=theme][value=dark]");
    page.check("#gear-dialog input[name=lang][value=pl]");
    assertThat(page.locator("#reports-toggle")).hasText("Raporty ▾");

    page.reload();

    assertThat(page.locator("html")).hasAttribute("data-theme", "dark");
    assertThat(page.locator("html")).hasAttribute("lang", "pl");
    page.click("#gear");
    assertThat(page.locator("#gear-dialog input[name=theme][value=dark]")).isChecked();
    assertThat(page.locator("#gear-dialog input[name=lang][value=pl]")).isChecked();
    assertThat(page.locator("#gear-dialog input[name=theme][value=dark]"))
        .hasAttribute("aria-checked", "true");
  }

  @Test
  void escapeClosesTheGearDialogAndFocusReturnsToTheGear() {
    Page page = openBoard();
    page.click("#gear");
    assertThat(page.locator("#gear-dialog")).isVisible();

    page.keyboard().press("Escape");

    assertThat(page.locator("#gear-dialog")).isHidden();
    assertThat(page.locator("#gear")).isFocused();
  }

  @Test
  void polishChosenOnTheGearReadsInPolishAndSurvivesReload() {
    Page page = openBoard();

    page.click("#gear");
    page.check("#gear-dialog input[name=lang][value=pl]");
    assertThat(page.locator("#reports-toggle")).hasText("Raporty ▾");
    page.reload();

    assertThat(page.locator("#reports-toggle")).hasText("Raporty ▾");
    assertThat(page.locator("html")).hasAttribute("lang", "pl");
  }

  @Test
  void keyMissingFromTheChosenCatalogShowsEnglish() throws IOException {
    Files.createDirectories(root.resolve("i18n"));
    Files.writeString(
        root.resolve("i18n/xx.json"),
        "{\"language.name\": \"Testish\", \"reports.toggle\": \"Reports-xx\"}");
    Files.writeString(root.resolve("i18n/index.json"), "[\"xx\"]");
    Page page = openBoard();

    page.click("#gear");
    page.check("#gear-dialog input[name=lang][value=xx]");

    assertThat(page.locator("#reports-toggle")).hasText("Reports-xx");
    assertThat(page.locator("#new-task span")).hasText("New task");
  }

  @Test
  void catalogInTheRootOverridesShippedKeyAndKeepsTheRest() throws IOException {
    Files.createDirectories(root.resolve("i18n"));
    Files.writeString(root.resolve("i18n/pl.json"), "{\"reports.toggle\": \"Zestawienia\"}");
    Page page = openBoard();

    page.click("#gear");
    page.check("#gear-dialog input[name=lang][value=pl]");

    assertThat(page.locator("#reports-toggle")).hasText("Zestawienia");
    assertThat(page.locator("#new-task span")).hasText("Nowe zadanie");
  }

  @Test
  void theRequestSaysHowOftenItsTaskWasShipped() {
    long now = System.currentTimeMillis();
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.CI_POLLING)
            .lastActiveTimestamp(now)
            .mrUrl("https://host/alpha/-/merge_requests/1")
            .mrCreatedAt(now)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, now - 3000, null),
                    new StatusChange(TaskStatus.IN_PROGRESS, now - 2000, null),
                    new StatusChange(TaskStatus.SHIPPING, now - 1000, null)))
            .build());

    Page page = openBoard();

    assertThat(page.locator("article .meta a.mr-age"))
        .hasAttribute("data-tip", Pattern.compile("shipped 2 times"));
  }

  @Test
  void saysNothingAboutPollForTaskThatIsNotOutForReview() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());

    Page page = openLiveBoard();

    assertThat(page.locator("article")).hasCount(1);
    assertThat(page.locator("article a.mr-age")).hasCount(0);
  }

  @Test
  void countsEveryPhaseWhetherOrNotItHoldsTask() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.REVIEW_PENDING).build());
    state.putTask(
        "ABC-3",
        TaskState.builder("beta", root.resolve("ABC-3-beta").toString(), TaskStatus.DEPLOYED)
            .alias("b1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = openLiveBoard();

    assertThat(page.locator("#phases .phase"))
        .hasText(new String[] {"build 1", "review 1", "check 0", "ready 0", "deploy 1", "done 0"});
    assertThat(page.locator("#phases"))
        .hasText("build 1 · review 1 · check 0 · ready 0 · deploy 1 · done 0 · order: added");
  }

  @Test
  void ordersTasksAsTheyWereRegisteredSoReusedAliasCannotMoveCardAlreadyOnTheBoard() {
    state.putTask("ABC-10", alphaTask("ABC-10", TaskStatus.IN_PROGRESS).build());
    state.putTask(
        "ABC-2",
        alphaTask("ABC-2", TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(System.currentTimeMillis() - 60_000)
            .build());

    Page page = openLiveBoard();

    assertThat(page.locator("#tasks .row .alias")).hasText(new String[] {"a10", "a2"});
  }

  @Test
  void ordersTasksByAliasWhenTheOrderControlIsPressed() {
    state.putTask("ABC-10", alphaTask("ABC-10", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.IN_PROGRESS).build());

    Page page = openLiveBoard();
    page.locator("#phases button.order").click();

    assertThat(page.locator("#tasks .row .alias")).hasText(new String[] {"a2", "a10"});
    assertThat(page.locator("#phases button.order")).hasText("order: alias");
  }

  @Test
  void theOpenTaskShowsTitleTooLongForItsRowWhole() {
    String whole = "A ticket whose first paragraph was pasted into its title".repeat(4);
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).title(whole).build());

    Page page = openLiveBoard();

    assertThat(page.locator("#pane article .title")).hasText(whole);
    assertThat(page.locator("#tasks .row-title")).hasText(whole);
  }

  @Test
  void rowTitleTooLongForItsRowShowsWholeOnHover() {
    String whole = "A ticket whose first paragraph was pasted into its title".repeat(4);
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).title(whole).build());
    Page page = openBoard();

    page.locator("#tasks .row-title").hover();

    assertThat(page.locator("#tip")).hasText(whole);
  }

  @Test
  void rowTitleThatFitsItsRowShowsNoHover() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).title("Short").build());
    Page page = openBoard();

    page.locator("#tasks .row-title").hover();

    assertThat(page.locator("#tip")).isHidden();
  }

  @Test
  void theOpenTaskShowsItsWholeLine() {
    String reason = "a merge conflict in a file whose path runs on ".repeat(4);
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.DEPLOY_CONFLICT).message(reason).build());

    Page page = openLiveBoard();

    assertThat(page.locator("#pane article .detail")).hasText("NEEDS YOU: " + reason);
  }

  @Test
  void showsOnlyThePhaseWhoseCountWasClicked() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.REVIEW_PENDING).build());

    Page page = openLiveBoard();
    page.locator("#phases button.phase").nth(1).click();

    assertThat(page.locator("article .alias")).hasText(new String[] {"a2"});
  }

  @Test
  void keepsEveryPhaseCountWhileOneOfThemIsTheFilter() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.REVIEW_PENDING).build());

    Page page = openLiveBoard();
    page.locator("#phases button.phase").nth(1).click();

    assertThat(page.locator("#phases")).containsText("build 1 · review 1");
  }

  @Test
  void refusesToFilterByPhaseThatHoldsNothing() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());

    Page page = openLiveBoard();

    assertThat(page.locator("#phases button.phase").nth(1)).isDisabled();
  }

  @Test
  void saysThatFiltersAreHidingEverythingRatherThanShowingBlankBoard() {
    state.putTask(
        "ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).title("Widget layout is off").build());

    Page page = openLiveBoard();
    page.locator("#filter").fill("nothing matches this");

    assertThat(page.locator("article")).hasCount(0);
    assertThat(page.locator("#filtered"))
        .hasText("No task matches: 1 filter(s) on, 1 task(s) hidden.");
  }

  @Test
  void clearsEveryFilterAtOnce() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.REVIEW_PENDING).build());

    Page page = openLiveBoard();
    page.locator("#phases button.phase").nth(1).click();
    page.locator("#phases .clear-filters").click();

    assertThat(page.locator("#tasks .row .alias")).hasText(new String[] {"a1", "a2"});
  }

  @Test
  void narrowsTheBoardToWhateverMatchesTheTypedText() {
    state.putTask(
        "ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).title("Widget layout is off").build());
    state.putTask(
        "ABC-2",
        alphaTask("ABC-2", TaskStatus.IN_PROGRESS).title("Invoice totals are wrong").build());

    Page page = openLiveBoard();
    page.locator("#filter").fill("invoice");

    assertThat(page.locator("article .alias")).hasText(new String[] {"a2"});
  }

  @Test
  void findsTaskByItsTicketNumberAsWellAsItsTitle() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    state.putTask(
        "XYZ-9",
        TaskState.builder("alpha", root.resolve("XYZ-9-alpha").toString(), TaskStatus.IN_PROGRESS)
            .alias("x1")
            .lastActiveTimestamp(System.currentTimeMillis())
            .build());

    Page page = openLiveBoard();
    page.locator("#filter").fill("xyz-9");

    assertThat(page.locator("article .alias")).hasText(new String[] {"x1"});
  }

  @Test
  void anEmptyBoardSaysWhereTaskComesFrom() {
    Page page = openLiveBoard();

    assertThat(page.locator("#empty")).containsText("No tasks yet.");
    assertThat(page.locator("#empty")).not().containsText("ABC-42");
  }

  @Test
  void saysNothingInTheHeaderAboutJobsWhileNoneHasFailed() {
    Page page = openLiveBoard();

    assertThat(page.locator("#jobs-pulse")).isHidden();
  }

  @Test
  void saysOnceInTheHeaderThatJagtLostItsReachAndLogsInFromThere() {
    reach.lost(
        new ReachLost(
            "glab",
            "git.example.invalid",
            List.of("glab", "auth", "login", "--hostname", "git.example.invalid"),
            "glab not logged in to git.example.invalid"));
    Page page = openBoard();

    assertThat(page.locator("#reach-lost")).hasText("glab not logged in to git.example.invalid");
    assertThat(page.locator("#reach-lost"))
        .hasAttribute(
            "data-tip", Pattern.compile("fix: glab auth login --hostname git.example.invalid"));
    page.locator("#reach-lost").click();

    verify(sessionHost, timeout(5_000))
        .openCommandWindow(
            any(),
            any(),
            eq("login"),
            eq(List.of("glab", "auth", "login", "--hostname", "git.example.invalid")));
    reach.reached("glab", "git.example.invalid");
  }

  @Test
  void reachLostWithNothingToFixIsInertAndItsHoverNamesTheNextProbe() {
    reach.lost(
        new ReachLost(
            "local-model",
            "model.example.invalid",
            List.of(),
            "local-model cannot reach model.example.invalid",
            System.currentTimeMillis() + Duration.ofMinutes(10).toMillis()));
    Page page = openBoard();

    assertThat(page.locator("#reach-lost")).isDisabled();
    assertThat(page.locator("#reach-lost"))
        .hasAttribute("data-tip", Pattern.compile("next read to probe: \\d+m"));
    reach.reached("local-model", "model.example.invalid");
  }

  @Test
  void keepsTheTabIconCalmWhereTheHumanStoppedTheBoard() throws Exception {
    Files.writeString(root.resolve("board-stopped"), Long.toString(System.currentTimeMillis()));
    Page page = openBoard();
    assertThat(page.locator("#role-chips button.chip")).isVisible();

    page.waitForTimeout(500);

    assertThat(page.locator("link[rel=icon][type='image/svg+xml']"))
        .hasAttribute("href", "favicon.svg");

    page.locator("#role-chips button.chip").click();

    assertThat(page.locator("#role-chips button.chip")).isHidden();
  }

  @Test
  void theTabIconKeepsTheLatestColourWhenAnEarlierIconReadFinishesLast() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.DEPLOY_CONFLICT).build());
    List<Route> iconReads = new java.util.ArrayList<>();
    Page page = session.newPage();
    page.route(
        "**/favicon.svg",
        route -> {
          if ("fetch".equals(route.request().resourceType())) {
            iconReads.add(route);
          } else {
            route.resume();
          }
        });
    page.navigate("http://localhost:" + port + "/");
    assertThat(page).hasTitle("(1) jagt");
    page.waitForCondition(() -> iconReads.size() == 1);

    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.CI_FAILED).build());
    page.waitForCondition(() -> iconReads.size() == 2);
    iconReads.get(1).resume();
    assertThat(page.locator("link[rel=icon][type='image/svg+xml']"))
        .hasAttribute("href", Pattern.compile("b42318"));
    iconReads.get(0).resume();
    page.waitForTimeout(300);

    assertThat(page.locator("link[rel=icon][type='image/svg+xml']"))
        .hasAttribute("href", Pattern.compile("b42318"));
  }

  @Test
  void givesActBackFromTheHeaderWhereItStoppedItself() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "brief");
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
    Page page = openBoard();
    assertThat(page.locator("#role-chips button.chip")).isVisible();

    page.locator("#role-chips button.chip").click();

    assertThat(page.locator("#role-chips button.chip")).isHidden();
  }

  @Test
  void drawsTheBoardTheHumanStoppedAsDashedPausedChip() throws Exception {
    Files.writeString(root.resolve("board-stopped"), Long.toString(System.currentTimeMillis()));

    Page page = openBoard();

    assertThat(page.locator("#role-chips button.chip.paused")).hasText("‖ board stopped");
  }

  @Test
  void drawsActJagtDroppedAsStalledChip() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "brief");
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

    Page page = openBoard();

    assertThat(page.locator("#role-chips button.chip.stalled")).hasText("■ act stopped");
  }

  @Test
  void drawsStartCutShortAsBadChip() throws Exception {
    Files.writeString(
        root.resolve("pending-starts.json"),
        "[{\"key\":\"ABC-42\",\"request\":{\"ref\":\"ABC-42\"},\"startedAt\":1000,"
            + "\"placement\":\"PLACED\"}]");

    Page page = openBoard();

    assertThat(page.locator("#role-chips span.chip.bad")).hasText("start interrupted");
  }

  @Test
  void anOpenTipOnTheJobsChipFollowsItsCountdownThroughRepaint() {
    breakable.breakIt();
    long now = System.currentTimeMillis();
    Page page = session.newPage();
    page.clock().setFixedTime(now - Duration.ofSeconds(630).toMillis());
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    jobs.runNow("breakable");
    page.waitForCondition(() -> jobs.summary(System.currentTimeMillis()).failing().size() > 0);
    state.putTask(
        "ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).lastActiveTimestamp(now).build());
    page.locator("#jobs-pulse").hover();
    assertThat(page.locator("#tip")).containsText("next run: 11m");

    page.clock().setFixedTime(now - Duration.ofSeconds(330).toMillis());
    state.putTask(
        "ABC-2", alphaTask("ABC-2", TaskStatus.IN_PROGRESS).lastActiveTimestamp(now).build());

    assertThat(page.locator("#tasks .row")).hasCount(2);
    assertThat(page.locator("#tip")).containsText("next run: 6m");
  }

  @Test
  void theJobsChipHoverNamesTheFailingJobAndItsCause() {
    breakable.breakIt();
    Page page = openBoard();
    jobs.runNow("breakable");
    page.waitForCondition(() -> jobs.summary(System.currentTimeMillis()).failing().size() > 0);
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());

    page.locator("#jobs-pulse").hover();

    assertThat(page.locator("#tip"))
        .containsText("breakable: java.lang.IllegalStateException: job broken");
  }

  @Test
  void theHeaderCountsTheTasksWhoseTurnItIs() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.REVIEWED).build());

    Page page = openLiveBoard();

    assertThat(page.locator("#waiting")).hasText("1 need your action");
  }

  @Test
  void theTitleHoverNamesTheRunningVersion() {
    Page page = openBoard();

    assertThat(page.locator("#title"))
        .hasAttribute("data-tip", Pattern.compile("^jagt \\d+\\.\\d+\\.\\d+"));
  }
}
