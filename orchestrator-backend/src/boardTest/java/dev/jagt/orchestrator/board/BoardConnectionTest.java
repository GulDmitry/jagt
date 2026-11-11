package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static dev.jagt.orchestrator.board.TestReplyDraftBuilder.replyDraft;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.surface.board.TaskEventStream;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.annotation.DirtiesContext;

class BoardConnectionTest extends BoardPageContext {

  @Autowired
  BoardConnectionTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void pageThatOutlivedRestartLoadsTheNewBoardWhenItReconnects() {
    Page page = session.newPage();
    page.route(
        "**/api/events",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setContentType("text/event-stream")
                    .setBody("event:open\ndata:second\n\n")));
    page.route(
        "**/api/events",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setContentType("text/event-stream")
                    .setBody("event:open\ndata:first\n\n")),
        new Page.RouteOptions().setTimes(1));
    page.navigate("http://localhost:" + port + "/");

    page.waitForRequest("http://localhost:" + port + "/", () -> {});
  }

  @Test
  void theManifestTheBoardPageLinksIsServedAsManifest() {
    Page page = openBoard();
    String href = (String) page.locator("link[rel=manifest]").evaluate("link => link.href");

    APIResponse manifest = session.request().get(Objects.requireNonNull(href));

    org.assertj.core.api.Assertions.assertThat(href)
        .isEqualTo("http://localhost:" + port + "/manifest.webmanifest");
    org.assertj.core.api.Assertions.assertThat(manifest.status()).isEqualTo(200);
    org.assertj.core.api.Assertions.assertThat(manifest.headers().get("content-type"))
        .startsWith("application/manifest+json");
  }

  @Test
  void stateChangeRepaintsAnOpenBoardWithNobodyReloadingIt() {
    Page page = openLiveBoard();

    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());

    assertThat(page.locator("article .alias")).hasText("a1");
  }

  @Test
  void pageReadsTheBoardAndItsVerbsOnceWhenItOpens() {
    List<String> reads = new java.util.concurrent.CopyOnWriteArrayList<>();
    session.onRequest(request -> reads.add(request.url().replaceFirst("^http://[^/]+", "")));

    Page page = openLiveBoard();
    assertThat(page.locator("#show-help")).hasCount(1);

    org.assertj.core.api.Assertions.assertThat(reads)
        .filteredOn(url -> url.equals("/api/tasks"))
        .hasSize(1);
    org.assertj.core.api.Assertions.assertThat(reads)
        .filteredOn(url -> url.equals("/api/commands"))
        .hasSize(1);
  }

  @Test
  void anOlderReadArrivingLastDoesNotPaintOverNewerOne() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openLiveBoard();
    List<Route> held = new java.util.concurrent.CopyOnWriteArrayList<>();
    List<APIResponse> read = new java.util.concurrent.CopyOnWriteArrayList<>();
    page.route(
        "**/api/tasks",
        route -> {
          read.add(route.fetch());
          held.add(route);
        });
    page.waitForRequest(
        "**/api/tasks",
        () ->
            state.putTask(
                "ABC-1",
                state
                    .task("ABC-1")
                    .orElseThrow()
                    .withStatus(TaskStatus.REVIEW_PENDING, "older", System.currentTimeMillis())));
    page.waitForCondition(() -> held.size() == 1);
    page.waitForRequest(
        "**/api/tasks",
        () ->
            state.putTask(
                "ABC-1",
                state
                    .task("ABC-1")
                    .orElseThrow()
                    .withStatus(TaskStatus.CI_POLLING, "newer", System.currentTimeMillis())));
    page.waitForCondition(() -> held.size() == 2);
    held.get(1).fulfill(new Route.FulfillOptions().setResponse(read.get(1)));
    assertThat(page.locator("article .status")).containsText("out for review");

    page.waitForRequestFinished(
        () -> held.get(0).fulfill(new Route.FulfillOptions().setResponse(read.get(0))));
    page.evaluate(
        "() => new Promise(done => setTimeout(() => requestAnimationFrame(() => done(true))))");

    org.assertj.core.api.Assertions.assertThat(page.locator("article .status").textContent())
        .contains("out for review");
  }

  @Test
  void repaintLeavesKeyboardFocusOnTheButtonItWasOn() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", alphaTask("ABC-2", TaskStatus.IN_PROGRESS).build());
    Page page = openLiveBoard();
    Locator focus =
        page.locator("article", new Page.LocatorOptions().setHasText("a1"))
            .getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Focus").setExact(true));
    focus.focus();

    state.putTask(
        "ABC-2",
        state
            .task("ABC-2")
            .orElseThrow()
            .withStatus(TaskStatus.REVIEW_PENDING, "done", System.currentTimeMillis()));

    assertThat(page.locator("#tasks .row .status"))
        .containsText(new String[] {"agent working", "review"});
    assertThat(focus).isFocused();
  }

  @Test
  void buttonPressedFromTheKeyboardKeepsTheFocusOnceItsMoveHasRun() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    when(commands.execute("ABC-1", TaskAction.FOCUS, null)).thenReturn("Focused ABC-1.");

    Page page = openLiveBoard();
    Locator focus = button(page, "Focus");
    focus.focus();
    focus.press("Enter");

    assertThat(page.locator("#toasts .toast")).hasText("Focused ABC-1.");
    assertThat(focus).isFocused();
  }

  @Test
  void repaintLeavesKeyboardFocusOnTheChipOfTheCardItRebuilt() {
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.IN_PROGRESS)
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge("abc1234")), 1, null))
            .build());
    Page page = openLiveBoard();
    page.locator("article .status").focus();

    state.putTask(
        "ABC-1",
        state
            .task("ABC-1")
            .orElseThrow()
            .withStatus(TaskStatus.REVIEW_PENDING, "done", System.currentTimeMillis()));

    assertThat(page.locator("article .status")).containsText("review");
    assertThat(page.locator("article .status")).isFocused();
  }

  @Test
  void repaintKeepsTheTipOfChipWhoseCardDidNotChange() {
    state.putTask(
        "ABC-1",
        alphaTask("ABC-1", TaskStatus.IN_PROGRESS)
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge("abc1234")), 1, null))
            .build());
    state.putTask(
        "ABC-2",
        alphaTask("ABC-2", TaskStatus.IN_PROGRESS)
            .deployment(
                new Deployment(
                    1, List.of(Deployment.Target.of("shop", "dev").withMerge("abc1234")), 1, null))
            .build());
    Page page = openLiveBoard();
    page.locator("article", new Page.LocatorOptions().setHasText("a1")).locator(".status").focus();
    assertThat(page.locator("#tip")).containsText("shared branch");

    state.putTask(
        "ABC-2",
        state
            .task("ABC-2")
            .orElseThrow()
            .withStatus(TaskStatus.REVIEW_PENDING, "done", System.currentTimeMillis()));

    assertThat(page.locator("#tasks .row .status"))
        .containsText(new String[] {"agent working", "review"});
    assertThat(page.locator("#tip")).isVisible();
  }

  @Test
  void boardCutOffFromTheBackendSaysItIsStaleAndOffersNothingUntilItReconnects() {
    Page page = session.newPage();
    page.route("**/api/events", Route::abort);
    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("#offline")).isVisible();
    assertThat(page.locator("#board")).hasAttribute("inert", "");

    page.unroute("**/api/events");

    assertThat(page.locator("#offline"))
        .isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(10_000));
    assertThat(page.locator("#board")).not().hasAttribute("inert", "");
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
  void roundOpenWhenTheBackendGoesAwayTakesNoLineForItsSession(
      @Autowired TaskEventStream events, @Autowired ApplicationContext context) throws IOException {
    replyDraft(root).withKey("ABC-18").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-18");
    Page page = openLiveBoard();
    ask(page, "replies a18");
    assertThat(page.locator("#report-line")).isVisible();

    events.onApplicationEvent(new ContextClosedEvent(context));

    assertThat(page.locator("#offline")).isVisible();
    assertThat(page.locator("#report-line")).hasAttribute("inert", "");
  }

  @Test
  void boardWhosePushConnectionFellSilentSaysItIsStaleAndConnectsAgain() {
    Page page = session.newPage();
    page.clock().install();
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));

    page.clock().runFor(50_500);
    assertThat(page.locator("#offline")).isVisible();
    page.waitForRequest("**/api/events", () -> page.clock().runFor(1_000));

    assertThat(page.locator("#offline")).isHidden();
  }

  @Test
  void boardWhosePushConnectionWasRefusedTriesAgainOnItsOwn() {
    Page page = session.newPage();
    page.route("**/api/events", route -> route.fulfill(new Route.FulfillOptions().setStatus(403)));
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("#offline")).isVisible();

    page.unroute("**/api/events");

    assertThat(page.locator("#offline"))
        .isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(10_000));
  }

  @Test
  void pageThatOutlivedRestartKeepsAnOpenFormUntilItCloses() {
    Page page = session.newPage();
    page.clock().install();
    page.navigate("http://localhost:" + port + "/");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    page.locator("#new-task").click();
    page.locator("#ref").fill("ABC-5");
    page.route(
        "**/api/events",
        route ->
            route.fulfill(
                new Route.FulfillOptions()
                    .setContentType("text/event-stream")
                    .setBody("event:open\ndata:another-run\n\n")));
    List<String> loads = new java.util.concurrent.CopyOnWriteArrayList<>();
    page.onRequest(
        request -> {
          if (request.isNavigationRequest()) {
            loads.add(request.url());
          }
        });

    page.clock().runFor(50_500);
    page.waitForRequest("**/api/events", () -> page.clock().runFor(1_000));
    page.waitForTimeout(500);

    org.assertj.core.api.Assertions.assertThat(loads).isEmpty();
    assertThat(page.locator("#ref")).hasValue("ABC-5");
    page.waitForRequest("http://localhost:" + port + "/", () -> page.keyboard().press("Escape"));
  }
}
