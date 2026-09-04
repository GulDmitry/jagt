package dev.jagt.orchestrator.board;

import dev.jagt.orchestrator.flow.Refusal;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.AgentSessions;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=",
                "orchestrator.open-terminal-window=false", "orchestrator.startup-checks=false"})
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
    private AutoReviewScheduler autoReviewScheduler;
    @MockitoBean
    private AgentSessions sessions;

    private BrowserContext session;

    @DynamicPropertySource
    static void keepConfigAndStateOutOfTheDevelopersOwnFiles(DynamicPropertyRegistry registry) {
        registry.add("orchestrator.root", () -> root.toString());
        registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
        registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
        registry.add("logging.file.name", () -> root.resolve("jagt.log").toString());
    }

    @BeforeAll
    static void startTheBrowserAndNameTheProjectsTheBoardOffers() throws IOException {
        Files.writeString(root.resolve("jagt.yml"), """
                orchestrator:
                  projects:
                    alpha: {path: "%s", baseBranch: origin/main, deployBranch: dev}
                    beta: {path: "%s", baseBranch: origin/main, deployBranch: dev}
                  autoReview: {enabled: true}
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
        assertThat(page.locator("article .meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("next poll 10m"));
    }

    @Test
    void saysNothingAboutAPollForATaskThatIsNotOutForReview() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article")).hasCount(1);
        assertThat(page.locator("article .pulse")).hasCount(0);
        assertThat(page.locator("article a.mr-age")).hasCount(0);
    }

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
        assertThat(page.locator("article .badge")).hasText("you can deploy it");
        assertThat(page.locator("article .title")).hasText("Widget layout is off");
        assertThat(page.locator("article .detail")).hasCount(0);
    }

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

    @Test
    void answersWhoseMoveItIsOnTheCardThatOffersNoButtonToPress() {
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

    @Test
    void doesNotShoutAboutAQuestionWhileAPollIsStillReadingTheRoundItWasAskedOn() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.REVIEW_PENDING).alias("a1").mrUrl("https://host.example/mr/7")
                .mrCreatedAt(now()).lastPolledAt(now()).message("awaiting: which lock do we take")
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .detail.problem")).hasCount(0);
        assertThat(page.locator("#waiting")).isHidden();
    }

    @Test
    void interruptsForABlockedTaskAndOnlyOffersTheNextMoveForOneThatCanWait() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_FAILED).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                        TaskStatus.APPROVED).alias("a2").mrUrl("https://host.example/mr/8")
                .lastActiveTimestamp(now()).build());

        Page page = open();

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
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .badge")).hasCount(0);
        assertThat(page.locator("#waiting")).isHidden();
    }

    @Test
    void aCardStaysInsideAWindowTooNarrowForItsBadgeAndItsTitleAtOnce() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_FAILED).alias("a1").mrUrl("https://host.example/mr/7")
                .title("Widget layout is off on every window narrower than a laptop screen")
                .lastActiveTimestamp(now()).build());

        Page page = open();
        page.setViewportSize(400, 1200);

        assertThat(page.locator("article"))
                .isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
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
                .contains("Only the LAST deploy comes out");
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
        when(launcher.launchLine(any())).thenThrow(new IllegalArgumentException("No ticket ABC-9 anywhere"));

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do ABC-9");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast.error")).containsText("No ticket ABC-9 anywhere");
    }

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
    void aReportThatAnswersForOneTaskGetsNoButtonInTheBarOfReports() {
        Page page = open();

        assertThat(page.locator("#show-stats")).hasCount(1);
        assertThat(page.locator("#show-replies")).hasCount(0);
    }

    @Test
    void draftedReviewRepliesAreAnnouncedOnTheCard() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nagreed, fixed\n");
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .drafts")).containsText("replies drafted");
    }

    @Test
    void theDraftedRepliesLineOpensEveryCommentAndTheReplyItWillSend() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-3-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## !12 thread 1\n> the canonical row count is wrong\nFIXED - Measured it and pinned the count.\n");
        state.putTask("ABC-3", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a3").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article .drafts").click();

        assertThat(page.locator("#report-body")).containsText("1 · FIXED · !12 thread 1");
        assertThat(page.locator("#report-body")).containsText("Measured it and pinned the count.");
    }

    @Test
    void theCommentAndTheAnswerToItAreToldApartWithoutASeparator() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-4-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## !12 thread 1\n> the canonical row count is wrong\nFIXED - Measured it and pinned the count.\n");
        state.putTask("ABC-4", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a4").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article .drafts").click();

        assertThat(page.locator("#report-body .verdict.ok")).hasText("FIXED ");
        assertThat(page.locator("#report-body .quote")).containsText("the canonical row count is wrong");
    }

    @Test
    void aLineSaidAtAnOpenRoundReachesTheSessionOfTheTaskItIsAbout() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-5-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-5", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a5").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());
        when(sessions.say("a5", "no, answer 1 differently")).thenReturn("Said to the agent.");

        Page page = open();
        page.locator("article .drafts").click();
        page.locator("#say").fill("no, answer 1 differently");
        page.locator("#say").press("Enter");

        assertThat(page.locator("#said")).isVisible();
        verify(sessions).say("a5", "no, answer 1 differently");
    }

    @Test
    void aReportThatIsNotAboutOneTaskOffersNoLineEvenWhenAnArgumentNamesOne() {
        state.putTask("ABC-12", TaskState.builder("alpha", root.resolve("ABC-12-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a12").lastActiveTimestamp(now()).build());

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("activity a12");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#report-title")).containsText("activity");
        assertThat(page.locator("#report-line")).isHidden();
    }

    @Test
    void aLineTypedAtOneRoundIsNotCarriedIntoTheNextOneOpened() throws IOException {
        Path first = Files.createDirectories(root.resolve("ABC-13-alpha"));
        Files.writeString(first.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        Path second = Files.createDirectories(root.resolve("ABC-14-alpha"));
        Files.writeString(second.resolve("review_replies.md"), "## thread 1\nFIXED - Pinned the count.\n");
        state.putTask("ABC-13", TaskState.builder("alpha", first.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a13").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());
        state.putTask("ABC-14", TaskState.builder("alpha", second.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a14").mrUrl("https://host.example/mr/8").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article:has-text(\"a13\") .drafts").click();
        page.locator("#say").fill("no, answer 1 differently");
        page.locator("#close-report").click();
        page.locator("article:has-text(\"a14\") .drafts").click();

        assertThat(page.locator("#say")).hasValue("");
    }

    @Test
    void whatTheWaitingRingMeansIsReadableOverTheDialogItPulsesIn() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-15-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-15", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a15").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());
        when(sessions.say("a15", "no, answer 1 differently")).thenReturn("Said to the agent.");

        Page page = open();
        page.locator("article .drafts").click();
        page.locator("#say").fill("no, answer 1 differently");
        page.locator("#say").press("Enter");
        page.locator("#said").hover();

        assertThat(page.locator("#report #tip")).isVisible();
    }

    @Test
    void anOpenRoundNobodyHasAnsweredYetShowsNothingWaiting() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-11-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-11", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a11").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article .drafts").click();

        assertThat(page.locator("#said")).isHidden();
    }

    @Test
    void aReportAnsweringForEveryTaskHasNoSessionToSayAnythingTo() {
        state.putTask("ABC-8", TaskState.builder("alpha", root.resolve("ABC-8-alpha").toString(),
                TaskStatus.REVIEW_PENDING).alias("a8").lastActiveTimestamp(now()).build());

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("replies");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#report")).isVisible();
        assertThat(page.locator("#report-line")).isHidden();
    }

    @Test
    void anOpenRoundIsReadAgainWhenTheAgentAnswersUnderIt() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-9-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-9", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a9").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article .drafts").click();
        Files.writeString(worktree.resolve("review_replies.md"),
                "## thread 1\nNO CHANGE - The name is the one the caller uses.\n");
        state.putTask("ABC-9", state.task("ABC-9").orElseThrow()
                .withStatus(TaskStatus.REVIEW_PENDING, "answered"));

        assertThat(page.locator("#report-body")).containsText("The name is the one the caller uses.");
    }

    @Test
    void aRoundRewrittenWhileItsReportWasStillBeingReadShowsWhatTheFileSaysNow() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-10-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-10", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a10").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());
        Page page = open();
        page.route("**/api/commands/replies**", route -> {
            APIResponse read = route.fetch();
            try {
                Files.writeString(worktree.resolve("review_replies.md"),
                        "## thread 1\nNO CHANGE - The name is the one the caller uses.\n");
            } catch (IOException cannotStageTheRewrite) {
                throw new UncheckedIOException(cannotStageTheRewrite);
            }
            route.fulfill(new Route.FulfillOptions().setResponse(read));
        });

        page.locator("article .drafts").click();

        assertThat(page.locator("#report-body")).containsText("The name is the one the caller uses.");
    }

    @Test
    void aReportsOwnLinksAreFollowableAndNothingElseBecomesOne() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-6-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## thread 1\n> see https://host.example/mr/7#note_8708.\nFIXED - javascript:alert(1) stays text.\n");
        state.putTask("ABC-6", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a6").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article .drafts").click();

        assertThat(page.locator("#report-body a"))
                .hasAttribute("href", "https://host.example/mr/7#note_8708");
        assertThat(page.locator("#report-body")).containsText("javascript:alert(1) stays text.");
        assertThat(page.locator("#report-body a")).hasCount(1);
    }

    @Test
    void aReportAboutOneTaskIsTitledByBothTheNamesItAnswersTo() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-7-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-7", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a7").mrUrl("https://host.example/mr/7").lastActiveTimestamp(now()).build());

        Page page = open();
        page.locator("article .drafts").click();

        assertThat(page.locator("#report-title")).hasText("replies a7 \u00b7 ABC-7");
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
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(new LaunchRequest("ABC-9", null, null, "fresh", null, null));
    }

    @Test
    void sendsTheExtraInstructionsTypedForTheAgent() {
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#notes").fill("start with the failing test");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(new LaunchRequest("ABC-9", null, null, "fresh", null,
                "start with the failing test"));
    }

    @Test
    void keepsEverythingTypedWhenTheLaunchCreatedNoTask() {
        when(launcher.launch(any()))
                .thenReturn(Launched.refused("error: read failed: ABC-9 (cause in the log) — no task created"));

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#base-branch").fill("feature/parent");
        page.locator("#notes").fill("start with the failing test");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).containsText("no task created");
        assertThat(page.locator("#ref")).hasValue("ABC-9");
        assertThat(page.locator("#base-branch")).hasValue("feature/parent");
        assertThat(page.locator("#notes")).hasValue("start with the failing test");
    }

    @Test
    void aTypedLaunchCarriesEveryModifierAndNotOnlyTheTicket() {
        when(launcher.launchLine(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do ABC-9 plan keep the API stable");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).containsText("Started ABC-9.");
        verify(launcher).launchLine("ABC-9 plan keep the API stable");
    }

    @Test
    void keepsTheTypedPaletteLineWhenTheLaunchItRanCreatedNoTask() {
        when(launcher.launchLine(any()))
                .thenReturn(Launched.refused("branch 'ABC-9' already exists in alpha (previous run)"));

        Page page = open();
        page.keyboard().press("Control+k");
        page.locator("#ask").fill("do ABC-9");
        page.locator("#ask").press("Enter");

        assertThat(page.locator("#toasts .toast")).containsText("already exists in alpha");
        assertThat(page.locator("#ask")).hasValue("do ABC-9");
    }

    @Test
    void showsWhatJagtDidOnItsOwnReadBackFromItsLog() throws IOException {
        Files.writeString(root.resolve("jagt.log"), """
                {"@timestamp":"2026-08-18T08:00:00Z","message":"sweep ABC-1: 2 thread(s) relayed","task":"ABC-1"}
                """, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

        Page page = open();
        page.locator("#show-activity").click();

        assertThat(page.locator("#report-title")).containsText("activity");
        assertThat(page.locator("#report-body")).containsText("2 thread(s) relayed");
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
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9 in beta."));

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#project").selectOption("beta");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9 in beta.");
        verify(launcher).launch(new LaunchRequest("ABC-9", "beta", null, "fresh", null, null));
    }

    @Test
    void startingATaskWithAChosenBranchStrategySendsIt() {
        when(launcher.launch(any())).thenReturn(Launched.created("ABC-9", "Started ABC-9."));

        Page page = open();
        page.locator("#ref").fill("ABC-9");
        page.locator("#strategy").selectOption("recreate");
        page.locator("#launch button[type=submit]").click();

        assertThat(page.locator("#toasts .toast")).hasText("Started ABC-9.");
        verify(launcher).launch(new LaunchRequest("ABC-9", null, null, "recreate", null, null));
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
    void aCardShowsTheChecksAsRedAndCarriesWhatTheHostSaidAboutThem() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("failed").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta .checks.red")).hasCount(1);
        assertThat(page.locator("article .meta .checks.red"))
                .hasAttribute("data-tip", Pattern.compile("checks: failed"));
    }

    @Test
    void aRunStillGoingPulsesBesideTheRequestInsteadOfColouringIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("running").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta .checks.running")).hasCount(1);
        assertThat(page.locator("article .meta a.mr-age")).hasClass("mr-age");
    }

    @Test
    void theRequestWearsATickOnceSomebodyHasApprovedIt() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .approved(false).lastActiveTimestamp(now()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                        TaskStatus.REVIEWED).alias("a2").mrUrl("https://host.example/mr/8")
                .approved(true).lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article").nth(0).locator(".meta a.mr-age")).hasText("MR");
        assertThat(page.locator("article").nth(0).locator(".meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("not approved yet"));
        assertThat(page.locator("article").nth(1).locator(".meta a.mr-age.approved")).hasText("MR \u2713");
    }

    @Test
    void aRedRunDoesNotSwallowTheApprovalItLandedOn() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("failed").approved(true).lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta a.mr-age.approved")).hasText("MR \u2713");
        assertThat(page.locator("article .meta .checks.red")).hasCount(1);
    }

    @Test
    void checksThatPassedWearTheirOwnDotAndLeaveTheRequestPlain() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("success").approved(false).lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta .checks.green"))
                .hasAttribute("data-tip", Pattern.compile("checks: success"));
        assertThat(page.locator("article .meta a.mr-age")).hasClass("mr-age");
    }

    @Test
    void aVerdictNothingCanReadWearsNoDotAndKeepsTheHostsWordOnTheRequest() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .pipelineStatus("completed").lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta .checks")).hasCount(0);
        assertThat(page.locator("article .meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("checks: completed"));
    }

    @Test
    void aRequestNoReadHasSeenYetWearsNoVerdictAndSaysSoOnHover() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.CI_POLLING).alias("a1").mrUrl("https://host.example/mr/7")
                .lastActiveTimestamp(now()).build());

        Page page = open();

        assertThat(page.locator("article .meta .checks")).hasCount(0);
        assertThat(page.locator("article .meta a.mr-age")).hasText("MR");
        assertThat(page.locator("article .meta a.mr-age"))
                .hasAttribute("data-tip", Pattern.compile("checks: nothing has read them yet"));
    }

    @Test
    void aTaskSpanningRepositoriesWearsOneTickForAllOfThem() {
        state.putTask("ABC-1", TaskState.builder(List.of(
                        new TaskRepo("alpha", root.resolve("ABC-1-alpha").toString(), null,
                                "https://host.example/alpha/mr/7", null),
                        new TaskRepo("beta", root.resolve("ABC-1-beta").toString(), null,
                                "https://host.example/beta/mr/7", null)),
                        TaskStatus.CI_POLLING).alias("a1").lastActiveTimestamp(now())
                .pipelineStatus("failed").approved(true).build());

        Page page = open();

        assertThat(page.locator("article .meta a.mr-age")).hasCount(2);
        assertThat(page.locator("article .meta .tick")).hasCount(1);
        assertThat(page.locator("article .meta .checks.red")).hasCount(1);
    }

    @Test
    void theDeployVerbIsColouredWhileItsLastRunIsStillLive() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.DEPLOYED).alias("a1").lastActiveTimestamp(now())
                .mrUrl("https://host.example/mr/7").deployCommit("abc1234").build());

        Page page = open();

        assertThat(page.locator("article button.again")).hasText("Deploy");
        assertThat(page.locator("article .meta .status.live")).hasCount(0);
    }

    @Test
    void theDeployVerbKeepsItsFillAndTakesTheMarkAsARingWhileItIsTheHighlightedMove() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1").lastActiveTimestamp(now())
                .mrUrl("https://host.example/mr/7").deployCommit("abc1234").build());

        Page page = open();

        assertThat(page.locator("article button.primary.again")).hasText("Deploy");
    }

    @Test
    void theStateSaysTheWorkIsLiveWhereNoVerbOnTheCardIsTheDeploy() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(now())
                .deployCommit("abc1234").build());

        Page page = open();

        assertThat(page.locator("article button.again")).hasCount(0);
        assertThat(page.locator("article .meta .status.live")).hasCount(1);
    }

    @Test
    void theDeployVerbIsPlainWhileNothingOfTheTaskIsLive() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                        TaskStatus.APPROVED).alias("a1").lastActiveTimestamp(now())
                .mrUrl("https://host.example/mr/7").build());

        Page page = open();

        assertThat(page.locator("article button[data-action=deploy]")).isVisible();
        assertThat(page.locator("article button.again")).hasCount(0);
    }

    @Test
    void helpAlsoSaysWhatTheBoardsOwnMarksMean() {
        Page page = open();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Help").setExact(true)).click();

        assertThat(page.locator("#report")).isVisible();
        assertThat(page.locator("#report-section .legend button.again")).hasText("Deploy");
        assertThat(page.locator("#report-section .legend .checks.red")).hasCount(1);
        assertThat(page.locator("#report-section .legend button.drafts")).hasCount(1);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private Page open() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        return page;
    }
}
