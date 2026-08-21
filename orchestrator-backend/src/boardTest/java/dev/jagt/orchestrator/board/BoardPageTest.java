package dev.jagt.orchestrator.board;

import dev.jagt.orchestrator.flow.Refusal;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.adapter.TtydWebTerminal;
import dev.jagt.orchestrator.service.AutoReviewScheduler;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.IdeRecentProjectsCleaner;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The board itself, in a browser: the columns it groups tasks into, the buttons it offers, what a click posts,
 * what a refusal reads like, the push that repaints it and the command palette. Nothing here asserts a rule of
 * jagt's own — the projection and the gate have their own tests — only that the page renders what the server
 * says and asks for exactly what the human clicked.
 *
 * <p>Three write paths are mocked because a real one would act on the developer's machine rather than on the
 * test: an action reaches git and tmux, a launch creates a worktree, and free text spends a model call. The
 * recent-projects cleaner is mocked for the same reason — its schedule rewrites the real IDE's own file.
 *
 * <p>Every seeded task is stamped as active NOW, because a task that has been silent for five minutes earns its
 * human a desktop alert.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"orchestrator.open-warp-window=false", "orchestrator.startup-checks=false"})
class BoardPageTest {

    @TempDir
    static Path root;

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    @Autowired
    private StateService state;

    @MockitoBean
    private CommandService commands;
    @MockitoBean
    private TaskLauncher launcher;
    @MockitoBean
    private NaturalLanguageDispatch naturalLanguage;
    @MockitoBean
    private IdeRecentProjectsCleaner ideRecentProjectsCleaner;
    @MockitoBean
    private TtydWebTerminal webTerminal;
    /** Polling is ON in the config so the page can show what it looks like; the poller itself would read a
     *  review request for real and, with no code host, pay a model to do it. */
    @MockitoBean
    private AutoReviewScheduler autoReviewScheduler;

    private BrowserContext session;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("config.json").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
        registry.add("logging.file.name", () -> root.resolve("jagt.log").toString());
    }

    @BeforeAll
    static void startTheBrowserAndNameTheProjectsTheBoardOffers() throws IOException {
        Files.writeString(root.resolve("config.json"), """
                {
                  "projects": {
                    "alpha": {"path": "%s", "baseBranch": "origin/main", "deployBranch": "dev"},
                    "beta": {"path": "%s", "baseBranch": "origin/main", "deployBranch": "dev"}
                  },
                  "autoReview": {"enabled": true}
                }
                """.formatted(root.resolve("alpha"), root.resolve("beta")));
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void stopTheBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void emptyTheBoard() throws IOException {
        Files.deleteIfExists(root.resolve("state.json"));
        Files.deleteIfExists(root.resolve("state.json.bak"));
        session = browser.newContext();
    }

    @AfterEach
    void closeTheTab() {
        session.close();
    }

    @Test
    void saysThatTheUnattendedPollIsOnAndWhenItWillNextLookAtATask() {
        long shipped = now();
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").lastActiveTimestamp(shipped)
                .mrUrl("https://host/alpha/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(shipped)
                .build());

        Page page = open();

        assertThat(page.locator("#auto-review")).hasText("auto-review on");
        assertThat(page.locator("#auto-review")).hasClass(java.util.regex.Pattern.compile("on"));
        assertThat(page.locator("article .pulse")).hasText("next poll 10m");
    }

    @Test
    void saysNothingAboutAPollForATaskThatIsNotOutForReview() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article")).hasCount(1);
        assertThat(page.locator("article .pulse")).hasCount(0);
    }

    /** Every phase is counted, zeros included: a line that appears and disappears moves everything beside it. */
    @Test
    void countsEveryPhaseWhetherOrNotItHoldsATask() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(now()).build());
        state.putTask("ABC-3", TaskState.builder("beta", root.resolve("ABC-3-beta").toString(),
                TaskStatus.DEPLOYED).alias("b1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("#phases .phase")).hasText(
                new String[]{"build 1", "review 1", "check 0", "ready 0", "deploy 1", "done 0"});
        assertThat(page.locator("#phases"))
                .hasText("build 1 · review 1 · check 0 · ready 0 · deploy 1 · done 0");
    }

    /**
     * A card keeps its place while its status changes under it, so the order cannot follow activity: the alias
     * is what a human types and what they remember the position by.
     */
    @Test
    void ordersTasksByAliasRatherThanByWhicheverAgentReportedLast() {
        state.putTask("ABC-10", TaskState.builder("alpha", root.resolve("ABC-10-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a10").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(now() - 60_000).build());

        Page page = open();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2", "a10"});
    }

    @Test
    void showsOnlyThePhaseWhoseCountWasClicked() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("#phases button.phase").nth(1).click();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2"});
    }

    /** A count that obeyed the phase choice would read zero the moment another phase was picked. */
    @Test
    void keepsEveryPhaseCountWhileOneOfThemIsTheFilter() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("#phases button.phase").nth(1).click();

        assertThat(page.locator("#phases")).containsText("build 1 · review 1");
    }

    @Test
    void refusesToFilterByAPhaseThatHoldsNothing() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("#phases button.phase").nth(1)).isDisabled();
    }

    @Test
    void saysThatFiltersAreHidingEverythingRatherThanShowingABlankBoard() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").title("Widget layout is off")
                .lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("#filter").fill("nothing matches this");

        assertThat(page.locator("article")).hasCount(0);
        assertThat(page.locator("#filtered")).hasText("No task matches: 1 filter(s) on, 1 task(s) hidden.");
    }

    @Test
    void clearsEveryFilterAtOnce() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("#phases button.phase").nth(1).click();
        page.locator("#phases .clear-filters").click();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a1", "a2"});
    }

    @Test
    void narrowsTheBoardToWhateverMatchesTheTypedText() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").title("Widget layout is off")
                .lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a2").title("Invoice totals are wrong")
                .lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("#filter").fill("invoice");

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2"});
    }

    @Test
    void findsATaskByItsTicketNumberAsWellAsItsTitle() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("XYZ-9", TaskState.builder("alpha", root.resolve("XYZ-9-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("x1").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("#filter").fill("xyz-9");

        assertThat(page.locator("article .alias")).hasText(new String[]{"x1"});
    }

    @Test
    void anEmptyBoardSaysWhereATaskComesFrom() {
        Page page = open();

        assertThat(page.locator("#empty")).containsText("No tasks.");
    }

    @Test
    void aCardShowsTheTaskAsTheProjectionDescribesIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1").title("Widget layout is off")
                .mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .alias")).hasText("a1");
        assertThat(page.locator("article .id")).hasText("ABC-1");
        assertThat(page.locator("article .badge")).hasText("action required");
        assertThat(page.locator("article .title")).hasText("Widget layout is off");
        assertThat(page.locator("article .detail")).hasCount(0);
    }

    /**
     * The status clock restarts on every round and on a respawned agent re-reporting itself, so how long the
     * review has been hanging is a clock of its own.
     */
    @Test
    void aCardSaysHowLongTheReviewRequestHasBeenOpen() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .requestOpenedAt(now() - java.time.Duration.ofHours(8).toMillis())
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .mr-age")).hasText("MR 8h");
    }

    @Test
    void offersTheRequestUnagedWhileNoReadHasSaidWhenItWasOpened() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .mr-age")).hasText("MR");
    }

    @Test
    void opensTheReviewRequestFromTheAgeItIsWearing() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());

        Page page = open();

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
                .requestOpenedAt(now() - java.time.Duration.ofHours(8).toMillis())
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .mr-age")).hasText(new String[]{"alpha MR", "beta MR"});
    }

    @Test
    void saysWhenTheNextUnattendedRunIsDueWithoutAReportBeingOpened() {
        Page page = open();

        assertThat(page.locator("#jobs-pulse")).containsText("jobs: next");
    }

    @Test
    void showsTheStatusInWordsWithItsOwnAgeInsideTheSameChip() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .status")).hasText("out for review 0m");
        assertThat(page.locator("article .status .age")).hasText("0m");
    }

    @Test
    void saysNothingUnderACardAboutARequestItAlreadyLinksTo() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("running").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .detail")).hasCount(0);
    }

    @Test
    void showsAPollThatStoppedInTheSlotThePulseWouldHaveUsed() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(now() - java.time.Duration.ofDays(9).toMillis())
                .lastPolledAt(now()).lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .pulse.stalled")).hasText("polling stopped");
    }

    @Test
    void namesThePollerOnceOnACardThatIsAlreadyItsPulse() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(now() - java.time.Duration.ofDays(9).toMillis())
                .lastPolledAt(now()).lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .pulse.stalled")).hasAttribute("data-tip",
                "no further polls: this round is past its 24h window");
    }

    @Test
    void opensTheTicketFromTheTaskNumber() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").ticketUrl("https://tracker.example/ABC-1")
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article a.id")).hasAttribute("href", "https://tracker.example/ABC-1");
    }

    @Test
    void showsTheTaskNumberAsPlainTextWhenNoTrackerGaveItAUrl() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article a.id")).hasCount(0);
    }

    /**
     * The one round that leaves no highlighted button and no badge, so its own line has to answer whose move it
     * is. A card carries no next-move line of its own: one that appears for some states and not others is a rule
     * nobody can read off the screen.
     */
    @Test
    void answersWhoseMoveItIsOnTheCardThatOffersNoButtonToPress() {
        // Stamped as a round the poller can time: a round it has STOPPED polling is a different card — that one
        // does ask for the human, since nothing else will read those threads again.
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.REVIEW_PENDING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(now()).lastPolledAt(now())
                .message("no changes: already handled").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .badge")).hasCount(0);
        assertThat(page.locator("article .hint")).hasCount(0);
        assertThat(page.locator("article .detail")).hasText(
                "ANSWERED: already handled — the open threads are the reviewer's to close");
    }

    /**
     * The change is live: what is left is the close, whenever the human wants it. A badge here reads as an alarm
     * beside the cards that really are blocked, which is what teaches them to ignore all three of badge, count
     * and filter.
     */
    @Test
    void asksForNothingOnACardWhoseChangeIsAlreadyDeployed() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .badge")).hasCount(0);
        assertThat(page.locator("#waiting")).isHidden();
    }

    @Test
    void namesTheOwnerOnlyWhenTheMoveIsYours() {
        long shipped = now();
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(shipped).lastPolledAt(shipped).lastActiveTimestamp(shipped).build());

        Page page = open();

        assertThat(page.locator("article .badge")).hasCount(0);
    }

    @Test
    void showsAHandEditedAliasAsTextRatherThanAsMarkup() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1<b>x</b>").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .alias")).hasText("a1<b>x</b>");
        assertThat(page.locator("article .alias b")).hasCount(0);
    }

    @Test
    void aCardGroupsWhatMovesTheTaskOnAwayFromWhatOnlyLooksAtItAndMarksTheObviousOne() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1")
                .mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();

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
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .actions.tool button.primary")).hasText("Focus");
        assertThat(page.locator("article .actions.tool button.primary")).hasCSS("color", "rgb(255, 255, 255)");
    }

    @Test
    void hoveringTheRevertButtonShowsThatItTakesOnlyTheLastDeployBackOut() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());

        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Revert").setExact(true)).hover();

        assertThat(page.locator("#tip")).isVisible();
        assertThat(page.locator("#tip"))
                .hasText("revert the last deploy's merge commit and push; earlier deploys stay live");
    }

    @Test
    void aTooltipGoesAwayWithThePointerThatOpenedIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).hover();
        page.locator("h1").hover();

        assertThat(page.locator("#tip")).isHidden();
    }

    @Test
    void revertingAsksFirstAndSaysOnlyTheLastDeployComesOut() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = open();
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Revert").setExact(true)).click();

        org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
                .startsWith("Revert ABC-1?")
                .contains("This pushes a revert commit to:")
                .contains("alpha → dev")
                .contains("Only the LAST deploy of this task comes out");
        verifyNoInteractions(commands);
    }

    @Test
    void clickingAnActionRunsItAndShowsTheSentenceItAnswered() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1 — its window is in front.");

        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).click();

        assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-1 — its window is in front.");
        verify(commands).execute("ABC-1", TaskAction.FOCUS);
    }

    @Test
    void focusShowsTheAgentsSessionOverTheBoardWhenATerminalServesIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");
        when(webTerminal.serve("jagt")).thenReturn(OptionalInt.of(8291));

        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).click();

        assertThat(page.locator("#terminal")).isVisible();
        assertThat(page.locator("#terminal-frame")).hasAttribute("src", "http://localhost:8291");
        assertThat(page.locator("#terminal-title")).containsText("a1");
    }

    @Test
    void focusOpensNoPanelWhenNoTerminalIsConfigured() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");

        Page page = open();
        page.waitForResponse(response -> response.url().endsWith("/api/tasks"), () ->
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true))
                        .click());

        verify(webTerminal).serve("jagt");
        assertThat(page.locator("#terminal")).isHidden();
    }

    @Test
    void closingTheTerminalPanelDetachesItFromTheSession() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");
        when(webTerminal.serve("jagt")).thenReturn(OptionalInt.of(8291));

        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).click();
        page.locator("#close-terminal").click();

        assertThat(page.locator("#terminal")).isHidden();
        assertThat(page.locator("#terminal-frame")).hasAttribute("src", "about:blank");
    }

    @Test
    void closingATaskAsksBeforeAnythingRuns() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = open();
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Done").setExact(true)).click();

        org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS)).startsWith("Done ABC-1?");
        verifyNoInteractions(commands);
    }

    /** The one click that writes a branch other people build on says exactly which branches, per repository. */
    @Test
    void deployingNamesEveryRepositoryAndTheBranchItWouldBePushedTo() throws Exception {
        state.putTask("ABC-1", TaskState.builder(List.of(
                        dev.jagt.orchestrator.task.TaskRepo.of("alpha", root.resolve("ABC-1-alpha").toString()),
                        new dev.jagt.orchestrator.task.TaskRepo("beta", root.resolve("ABC-1-beta").toString(),
                                null, "https://host.example/mr/8", null)),
                TaskStatus.REVIEW_PENDING).alias("a1").lastActiveTimestamp(now()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = open();
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Deploy").setExact(true)).click();

        String question = asked.get(5, TimeUnit.SECONDS);
        org.assertj.core.api.Assertions.assertThat(question)
                .startsWith("Deploy ABC-1?")
                .contains("This merges and pushes:")
                .contains("alpha → dev")
                .contains("beta → dev");
        verifyNoInteractions(commands);
    }

    /**
     * Whether the work is worth shipping first is the human's call, and the readings jagt tried were wrong often
     * enough to be clicked past — which is what costs the branch lines their reader.
     */
    @Test
    void deployingAsksAboutTheBranchesAndAdvisesNothingAboutTheRound() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nagreed, fixed\n");
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").mrUrl("https://host.example/mr/9").message("widget layout fixed")
                .lastActiveTimestamp(now()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = open();
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Deploy").setExact(true)).click();

        org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
                .isEqualTo("Deploy ABC-1?\n\nThis merges and pushes:\nalpha → dev");
        verifyNoInteractions(commands);
    }

    @Test
    void aRefusedActionAlsoSaysTheBoardHasCaughtUp() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenThrow(new Refusal(
                Refusal.Code.ACTION_NOT_AVAILABLE, "Ship is not available for ABC-1 (it is DEPLOYED)"));

        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ship").setExact(true)).click();

        assertThat(page.locator("#toasts .toast.error"))
                .containsText("Ship is not available for ABC-1 (it is DEPLOYED)");
        assertThat(page.locator("#toasts .toast.error")).containsText("The board is up to date now.");
    }

    /**
     * The latch IS the behaviour: what a card offers while a move is still running cannot be asserted without
     * holding one there.
     */
    @Test
    void aMoveInFlightLocksTheButtonsThatWriteAndLeavesTheLookOnlyOnesClickable() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());
        CountDownLatch sweeping = new CountDownLatch(1);
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenAnswer(invocation -> {
            sweeping.await(10, TimeUnit.SECONDS);
            return "sweep ABC-1: checks success";
        });

        Page page = open();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Check review").setExact(true)).click();

        try {
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Focus").setExact(true))).isEnabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Open IDE").setExact(true))).isEnabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Diff").setExact(true))).isEnabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Ship").setExact(true))).isDisabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Restart agent").setExact(true))).isDisabled();
        } finally {
            sweeping.countDown();
        }
    }

    @Test
    void aLookOnlyClickThatFinishesLeavesTheLockOfTheMoveStillRunning() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());
        CountDownLatch sweeping = new CountDownLatch(1);
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenAnswer(invocation -> {
            sweeping.await(10, TimeUnit.SECONDS);
            return "sweep ABC-1: checks success";
        });
        when(commands.execute("ABC-1", TaskAction.DIFF)).thenReturn("Opened the diff for ABC-1.");

        Page page = open();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Check review").setExact(true)).click();

        try {
            page.waitForResponse(
                    response -> response.url().endsWith("/api/tasks")
                            && "GET".equals(response.request().method()),
                    () -> page.getByRole(AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("Diff").setExact(true)).click());

            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Ship").setExact(true))).isDisabled();
        } finally {
            sweeping.countDown();
        }
    }

    @Test
    void aTypedMoveIsRefusedWhileAnotherOneIsStillRunningOnTheSameTask() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());
        CountDownLatch sweeping = new CountDownLatch(1);
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenAnswer(invocation -> {
            sweeping.await(10, TimeUnit.SECONDS);
            return "sweep ABC-1: checks success";
        });

        Page page = open();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Check review").setExact(true)).click();

        try {
            page.keyboard().press("Control+k");
            page.locator("#ask").fill("ship a1");
            page.locator("#ask").press("Enter");

            assertThat(page.locator("#toasts .toast.error")).containsText("already running sweep");
            verify(commands, never()).execute("ABC-1", TaskAction.SHIP);
        } finally {
            sweeping.countDown();
        }
    }

    @Test
    void aTypedLaunchThatIsRefusedSaysSoInsteadOfSittingThere() {
        when(launcher.launch(any())).thenThrow(new IllegalArgumentException("No ticket ABC-9 anywhere"));

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do ABC-9");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast.error")).containsText("No ticket ABC-9 anywhere");
    }

    /** An alias is short enough to be another task's ticket id, and the card carries the id. */
    @Test
    void aClickActsOnTheTaskWhoseCardItIsWhenAnotherTasksAliasReadsLikeItsId() {
        state.putTask("4", TaskState.builder("alpha", root.resolve("4-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("41").lastActiveTimestamp(now()).build());
        state.putTask("41", TaskState.builder("alpha", root.resolve("41-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("42").lastActiveTimestamp(now()).build());
        when(commands.execute("41", TaskAction.SHIP)).thenReturn("Shipped 41.");

        Page page = open();
        page.locator("article", new Page.LocatorOptions().setHas(page.locator(".alias:text-is(\"42\")")))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ship").setExact(true))
                .click();

        assertThat(page.locator("#toasts .toast")).hasText("Shipped 41.");
        verify(commands, never()).execute("4", TaskAction.SHIP);
    }

    @Test
    void aStateChangeRepaintsAnOpenBoardWithNobodyReloadingIt() {
        Page page = open();

        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        assertThat(page.locator("article .alias")).hasText("a1");
    }

    @Test
    void theHeaderCountsTheTasksWhoseTurnItIs() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEWED).alias("a2").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("#waiting")).hasText("1 need your action");
    }

    @Test
    void waitingOnMeLeavesOnlyTheTasksWhoseTurnItIs() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEWED).alias("a2").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("#mine").check();

        assertThat(page.locator("article .alias")).hasText(new String[]{"a2"});
    }

    @Test
    void draftedReviewRepliesAreAnnouncedOnTheCard() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nagreed, fixed\n");
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .drafts")).containsText("review replies drafted, not posted");
    }

    @Test
    void theDraftedRepliesLineOpensEveryCommentAndTheReplyItWillSend() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## !12 thread 1\n> the canonical row count is wrong\nFIXED - Measured it and pinned the count.\n");
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article .drafts").click();

        assertThat(page.locator("#report-body")).containsText("1 · FIXED · !12 thread 1");
        assertThat(page.locator("#report-body")).containsText("Measured it and pinned the count.");
    }

    @Test
    void aTypedReportNarrowsToTheTaskItNamesInsteadOfAnsweringForAllOfThem() throws IOException {
        Path drafting = Files.createDirectories(root.resolve("ABC-2-alpha"));
        Files.writeString(drafting.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", drafting.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a2").lastActiveTimestamp(now()).build());

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("replies a1");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#report-body")).containsText("a1 has no drafted replies");
    }

    @Test
    void thePaletteConfirmsALineItCanRunAsTyped() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship a1");

        assertThat(page.locator("#palette-state")).containsText("runs as typed");
    }

    @Test
    void thePaletteExecutesACommandItUnderstandsWithoutPayingForTheModel() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.SHIP)).thenReturn("Shipped ABC-1 — review request updated.");

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship a1");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("Shipped ABC-1 — review request updated.");
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void thePaletteSaysSoBeforeRunningWhenItCannotFindTheTask() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship nope");

        assertThat(page.locator("#palette-state")).hasClass(Pattern.compile("\\bbad\\b"));
        assertThat(page.locator("#palette-state")).containsText("no task");
    }

    @Test
    void thePaletteRunsARetiredVerbItselfInsteadOfPayingTheModelForIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host/x/merge_requests/7")
                .lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.SWEEP)).thenReturn("sweep ABC-1: checks success");

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("review a1");

        assertThat(page.locator("#palette-state")).containsText("runs as typed");

        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("sweep ABC-1: checks success");
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void freeTextIsInterpretedAndTheBoardLeadsWithHowItWasUnderstood() {
        when(naturalLanguage.interpret("send the widget work out for review"))
                .thenReturn("understood as `ship a1` — Shipped ABC-1.");

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("send the widget work out for review");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("understood as `ship a1` — Shipped ABC-1.");
    }

    @Test
    void startingATaskWithoutPickingAProjectLeavesTheChoiceToTheTicketRead() {
        when(launcher.launch(any())).thenReturn("Started ABC-9.");

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(new LaunchRequest("ABC-9", null, null, null, null, null));
    }

    /**
     * The one field on the page whose loss is silent: an agent given no extra instructions simply works without
     * them, and nothing on either surface says the human typed any.
     */
    @Test
    void sendsTheExtraInstructionsTypedForTheAgent() {
        when(launcher.launch(any())).thenReturn("Started ABC-9.");

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#notes").fill("start with the failing test");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(new LaunchRequest("ABC-9", null, null, null, null,
                "start with the failing test"));
    }

    @Test
    void showsWhatJagtDidOnItsOwnReadBackFromItsLog() throws IOException {
        Files.writeString(root.resolve("jagt.log"), """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"sweep ABC-1: 2 comment(s) relayed","task":"ABC-1"}
                """, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

        Page page = open();
        page.locator("#show-activity").click();

        assertThat(page.locator("#report-title")).containsText("activity");
        assertThat(page.locator("#report-body")).containsText("2 comment(s) relayed");
    }

    @Test
    void offersTheProjectsWithNonePickedSoAMultiSelectStartsEmpty() {
        Page page = open();

        assertThat(page.locator("#project option")).hasCount(2);
        assertThat(page.locator("#project")).hasValues(new String[]{});
    }

    @Test
    void answersATasklessVerbItselfInsteadOfSendingItToTheModel() {
        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("ship");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#palette-state")).containsText("ship needs a task");
        verifyNoInteractions(naturalLanguage);
    }

    @Test
    void startingATaskWithAPickedProjectSendsThatProject() {
        when(launcher.launch(any())).thenReturn("Started ABC-9 in beta.");

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#project").selectOption("beta");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9 in beta.");
        verify(launcher).launch(new LaunchRequest("ABC-9", "beta", null, null, null, null));
    }

    @Test
    void aReportClosesWhenTheDimmedAreaAroundItIsClicked() {
        Page page = open();
        page.locator("#show-activity").click();
        assertThat(page.locator("#report")).isVisible();

        page.mouse().click(4, 4);

        assertThat(page.locator("#report")).isHidden();
    }

    @Test
    void aReportSurvivesASelectionThatStartedInsideItAndEndedOutside() {
        Page page = open();
        page.locator("#show-activity").click();

        page.locator("#report-body").hover();
        page.mouse().down();
        page.mouse().move(4, 4);
        page.mouse().up();

        assertThat(page.locator("#report")).isVisible();
    }

    @Test
    void theTerminalPanelClosesOnTheDimmedAreaToo() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");
        when(webTerminal.serve("jagt")).thenReturn(OptionalInt.of(8291));

        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true)).click();
        assertThat(page.locator("#terminal")).isVisible();

        page.mouse().click(4, 4);

        assertThat(page.locator("#terminal")).isHidden();
    }

    /**
     * The status word cannot carry this: a task sits at CI_POLLING whether its run is still going or already
     * red, and only the host's own wording says which failure it was.
     */
    @Test
    void aCardShowsTheChecksAsRedAndCarriesWhatTheHostSaidAboutThem() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("failed").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta .checks.red")).hasCount(1);
        assertThat(page.locator("article .meta .checks.red")).hasAttribute("data-tip", "checks: failed");
    }

    /**
     * Whether anyone has approved decides whether this card is waiting on a person or on the human reading it,
     * and the status only ever says so once the approval has already landed.
     */
    @Test
    void aCardShowsTheApprovalBesideTheRequestAsAnEmptyRingUntilSomebodyApproves() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .approved(false).lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                        TaskStatus.REVIEWED).alias("a2").mrUrl("https://host.example/mr/8")
                .approved(true).lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article").nth(0).locator(".meta .approval:not(.yes)")).hasCount(1);
        assertThat(page.locator("article").nth(0).locator(".meta .approval"))
                .hasAttribute("data-tip", "review request not approved yet");
        assertThat(page.locator("article").nth(1).locator(".meta .approval.yes")).hasCount(1);
    }

    /** A round nobody has read carries no verdict, and an unread request is not an unapproved one. */
    @Test
    void aCardShowsNoApprovalDotBeforeAnyReadHasSaidEitherWay() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .approval")).hasCount(0);
    }

    @Test
    void aCardShowsNoChecksDotWhenNoPipelineHasBeenReadYet() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article")).hasCount(1);
        assertThat(page.locator("article .checks")).hasCount(0);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * A tab whose event stream is already connected, so a state change made after it cannot be missed.
     */
    private Page open() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        return page;
    }
}
