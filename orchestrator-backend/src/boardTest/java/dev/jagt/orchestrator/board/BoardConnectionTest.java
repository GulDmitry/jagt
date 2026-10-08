package dev.jagt.orchestrator.board;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.surface.board.TaskEventStream;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.when;

class BoardConnectionTest extends BoardPageContext {

    @Test
    void aStateChangeRepaintsAnOpenBoardWithNobodyReloadingIt() {
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());

        assertThat(page.locator("article .alias")).hasText("a1");
    }

    @Test
    void aPageReadsTheBoardAndItsVerbsOnceWhenItOpens() {
        List<String> reads = new java.util.concurrent.CopyOnWriteArrayList<>();
        session.onRequest(request -> reads.add(request.url().replaceFirst("^http://[^/]+", "")));

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        assertThat(page.locator("#show-help")).isVisible();

        org.assertj.core.api.Assertions.assertThat(reads).filteredOn(url -> url.equals("/api/tasks")).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(reads).filteredOn(url -> url.equals("/api/commands")).hasSize(1);
    }

    @Test
    void anOlderReadArrivingLastDoesNotPaintOverANewerOne() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        List<Route> held = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<APIResponse> read = new java.util.concurrent.CopyOnWriteArrayList<>();
        page.route("**/api/tasks", route -> {
            read.add(route.fetch());
            held.add(route);
        });
        page.waitForRequest("**/api/tasks", () -> state.putTask("ABC-1",
                state.task("ABC-1").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "older")));
        page.waitForCondition(() -> held.size() == 1);
        page.waitForRequest("**/api/tasks", () -> state.putTask("ABC-1",
                state.task("ABC-1").orElseThrow().withStatus(TaskStatus.CI_POLLING, "newer")));
        page.waitForCondition(() -> held.size() == 2);
        held.get(1).fulfill(new Route.FulfillOptions().setResponse(read.get(1)));
        assertThat(page.locator("article .status")).containsText("out for review");

        page.waitForRequestFinished(() -> held.get(0).fulfill(new Route.FulfillOptions().setResponse(read.get(0))));

        org.assertj.core.api.Assertions.assertThat(page.locator("article .status").textContent())
                .contains("out for review");
    }

    @Test
    void aRepaintLeavesKeyboardFocusOnTheButtonItWasOn() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("ABC-2-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").lastActiveTimestamp(System.currentTimeMillis()).build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        Locator focus = page.locator("article", new Page.LocatorOptions().setHasText("a1"))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Focus").setExact(true));
        focus.focus();

        state.putTask("ABC-2", state.task("ABC-2").orElseThrow().withStatus(TaskStatus.REVIEW_PENDING, "done"));

        assertThat(page.locator("article .status")).containsText(new String[]{"agent working", "review"});
        assertThat(focus).isFocused();
    }

    @Test
    void aButtonPressedFromTheKeyboardKeepsTheFocusOnceItsMoveHasRun() {
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("ABC-1-alpha").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").lastActiveTimestamp(System.currentTimeMillis()).build());
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("Focused ABC-1.");

        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        Locator focus = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Focus").setExact(true));
        focus.focus();
        focus.press("Enter");

        assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-1.");
        assertThat(focus).isFocused();
    }

    @Test
    void aRepaintLeavesKeyboardFocusOnTheReportTheCardOffers() throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-17-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-17", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a17").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        Locator offer = page.locator("article .offer");
        offer.focus();

        state.putTask("ABC-17", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a17").title("Renamed").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System
                .currentTimeMillis()).build());

        assertThat(page.locator("article .title")).hasText("Renamed");
        assertThat(offer).isFocused();
    }

    @Test
    void aBoardCutOffFromTheBackendSaysItIsStaleAndOffersNothingUntilItReconnects() {
        Page page = session.newPage();
        page.route("**/api/events", Route::abort);
        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator("#offline")).isVisible();
        assertThat(page.locator("#board")).hasAttribute("inert", "");

        page.unroute("**/api/events");

        assertThat(page.locator("#offline")).isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(10_000));
        assertThat(page.locator("#board")).not().hasAttribute("inert", "");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void aRoundOpenWhenTheBackendGoesAwayTakesNoLineForItsSession(@Autowired TaskEventStream events,
                                                                   @Autowired ApplicationContext context)
            throws IOException {
        Path worktree = Files.createDirectories(root.resolve("ABC-18-alpha"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        state.putTask("ABC-18", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a18").mrUrl("https://host.example/mr/7").lastActiveTimestamp(System.currentTimeMillis())
                .build());
        Page page = session.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));
        page.locator("article .offer").click();
        assertThat(page.locator("#report-line")).isVisible();

        events.onApplicationEvent(new ContextClosedEvent(context));

        assertThat(page.locator("#offline")).isVisible();
        assertThat(page.locator("#report-line")).hasAttribute("inert", "");
    }

    @Test
    void aBoardWhosePushConnectionFellSilentSaysItIsStaleAndConnectsAgain() {
        Page page = session.newPage();
        page.clock().install();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#live")).hasClass(Pattern.compile("\\bon\\b"));

        page.clock().runFor(50_500);
        assertThat(page.locator("#offline")).isVisible();
        page.waitForRequest("**/api/events", () -> page.clock().runFor(1_000));

        assertThat(page.locator("#offline")).isHidden();
    }

    @Test
    void aBoardWhosePushConnectionWasRefusedTriesAgainOnItsOwn() {
        Page page = session.newPage();
        page.route("**/api/events", route -> route.fulfill(new Route.FulfillOptions().setStatus(403)));
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator("#offline")).isVisible();

        page.unroute("**/api/events");

        assertThat(page.locator("#offline")).isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(10_000));
    }
}
