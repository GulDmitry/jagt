package dev.jagt.orchestrator.board;

import dev.jagt.orchestrator.flow.Refusal;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        assertThat(page.locator("article .watch")).hasText("auto-review · next poll in 10m");
    }

    @Test
    void saysNothingAboutAPollForATaskThatIsNotOutForReview() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article")).hasCount(1);
        assertThat(page.locator("article .watch")).hasCount(0);
    }

    @Test
    void onlyThePhasesThatHaveATaskGetAColumn() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a2").lastActiveTimestamp(now()).build());
        state.putTask("ABC-3", TaskState.builder("beta", root.resolve("ABC-3-beta").toString(),
                TaskStatus.DEPLOYED).alias("b1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("#board > section > h2"))
                .hasText(new String[]{"build 1", "review 1", "deploy 1"});
    }

    @Test
    void anEmptyBoardSaysWhereATaskComesFrom() {
        Page page = open();

        assertThat(page.locator("#empty")).containsText("No tasks.");
    }

    @Test
    void aCardShowsTheTaskAsTheProjectionDescribesIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.REVIEWED).alias("a1").title("Widget layout is off")
                .mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .alias")).hasText("a1");
        assertThat(page.locator("article .id")).hasText("ABC-1");
        assertThat(page.locator("article .badge")).hasText("your move");
        assertThat(page.locator("article .title")).hasText("Widget layout is off");
        assertThat(page.locator("article .hint")).hasText("your move: deploy or done");
        assertThat(page.locator("article .links a")).hasText(new String[]{"review request"});
        assertThat(page.locator("article .detail")).hasCount(0);
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
                        TaskStatus.REVIEWED).alias("a1")
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
                .hasText("undo the LAST deploy only, earlier ones stay live: revert that merge and push");
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

    /** A deploy lands what was SHIPPED, and a round that came back is not that — the question has to say so. */
    @Test
    void deployingWarnsWhenTheTaskHasWorkThatWasNeverShipped() throws Exception {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.REVIEW_PENDING).alias("a1").mrUrl("https://host.example/mr/9")
                .lastActiveTimestamp(now()).build());
        CompletableFuture<String> asked = new CompletableFuture<>();

        Page page = open();
        page.onDialog(dialog -> {
            asked.complete(dialog.message());
            dialog.dismiss();
        });
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Deploy").setExact(true)).click();

        org.assertj.core.api.Assertions.assertThat(asked.get(5, TimeUnit.SECONDS))
                .contains("never shipped")
                .contains("lands the last SHIP");
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

        assertThat(page.locator("#waiting")).hasText("1 waiting on you");
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

        assertThat(page.locator("article .drafts")).containsText("drafted review replies");
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
    void labelsTheSessionClockSoItDoesNotReadAsASecondAgeOfTheStatus() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta span").last()).containsText("active");
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
