package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.surface.ui.log.RoleFeeds.RoleFeedWritten;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class BoardRoleLogsTest extends BoardPageContext {

  private final ApplicationEventPublisher events;

  @Autowired
  BoardRoleLogsTest(BreakableJob breakable, StateService state, ApplicationEventPublisher events) {
    super(breakable, state);
    this.events = events;
  }

  @Test
  void showsOneChipForEachRunningModelRoleAndNoneWhereNoneRuns() {
    Page page = session.newPage();
    page.route(
        "**/api/tasks",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setResponse(route.fetch())
                    .setBody(
                        route
                            .fetch()
                            .text()
                            .replaceFirst(
                                "\"roleLogs\":\\[[^\\]]*\\]",
                                "\"roleLogs\":[\"acceptance\",\"master\"]"))));

    page.navigate("http://localhost:" + port + "/");

    assertThat(page.locator("#role-logs button.chip"))
        .hasText(new String[] {"acceptance", "master"});

    Page quiet = session.newPage();
    quiet.route(
        "**/api/tasks",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setResponse(route.fetch())
                    .setBody(
                        route
                            .fetch()
                            .text()
                            .replaceFirst("\"roleLogs\":\\[[^\\]]*\\]", "\"roleLogs\":[]"))));

    quiet.navigate("http://localhost:" + port + "/");

    assertThat(quiet.locator("#role-logs button.chip")).hasCount(0);
  }

  @Test
  void theRoleChipOpensItsLogInThePaneFollowingNewLinesAndHidesItOnTheNextPress() {
    Page page = session.newPage();
    page.route(
        "**/api/tasks",
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setResponse(route.fetch())
                    .setBody(
                        route
                            .fetch()
                            .text()
                            .replaceFirst(
                                "\"roleLogs\":\\[[^\\]]*\\]", "\"roleLogs\":[\"master\"]"))));
    page.route(
        Pattern.compile(".*/api/roles/master/log.*"),
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody(
                        route.request().url().endsWith("after=0")
                            ? "{\"lines\":[\"2026-11-25 09:00:01Z INFO first step\"],"
                                + "\"next\":1}"
                            : "{\"lines\":[\"2026-11-25 09:00:02Z INFO second step\"],"
                                + "\"next\":2}")));
    page.navigate("http://localhost:" + port + "/");

    page.locator("#role-logs button.chip").click();

    assertThat(page.locator("#pane.role-log .log-line")).hasText(Pattern.compile("first step"));
    assertThat(page.locator("#role-logs button.chip")).hasAttribute("aria-pressed", "true");

    events.publishEvent(new RoleFeedWritten("master"));

    assertThat(page.locator("#pane.role-log .log-line"))
        .hasText(new Pattern[] {Pattern.compile("second step"), Pattern.compile("first step")});

    page.locator("#role-logs button.chip").click();

    assertThat(page.locator("#pane.role-log")).hasCount(0);
    assertThat(page.locator("#role-logs button.chip")).hasAttribute("aria-pressed", "false");
  }

  @Test
  void theMastersLogListsItsNewestEntryOnTopWithGapsBetweenEntries() {
    Page page =
        openRoleLog(
            "master",
            "{\"lines\":[\"2026-11-25 09:00:01Z INFO first step\","
                + "\"2026-11-25 09:00:02Z INFO second step\"],\"next\":2}");

    assertThat(page.locator("#pane.role-log .log-line"))
        .hasText(new Pattern[] {Pattern.compile("second step"), Pattern.compile("first step")});
    assertThat(page.locator("#pane.role-log .log-line").first()).hasCSS("margin-bottom", "6px");
    assertThat(page.locator("#pane.role-log .log-time").first()).hasText("09:00:02");
  }

  @Test
  void emptyLogStandsWhereItsFirstEntryWillWithNoTaskFilterOrListOnAnEmptyBoard() {
    Page page = openRoleLog("master", "{\"lines\":[],\"next\":0}");

    assertThat(page.locator("#pane.role-log .log-lines > :first-child"))
        .hasText("nothing logged by master yet");
    assertThat(page.locator("#phases")).isHidden();
    assertThat(page.locator("#tasks")).hasCount(0);
    assertThat(page.locator("#empty")).isHidden();
    assertThat(page.locator("#pane.role-log .back")).hasText("‹ all tasks · master log");

    page.route(
        Pattern.compile(".*/api/roles/master/log.*"),
        route ->
            route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                    .setContentType("application/json")
                    .setBody(
                        "{\"lines\":[\"2026-11-25 09:00:01Z INFO first step\"]," + "\"next\":1}")));
    events.publishEvent(new RoleFeedWritten("master"));

    assertThat(page.locator("#pane.role-log .log-lines > *")).hasCount(1);
    assertThat(page.locator("#pane.role-log .log-lines > :first-child"))
        .hasText(Pattern.compile("first step"));
  }

  @Test
  void phaseBarHidesBesideTheLogWhileTheFilterLeavesNoTaskShowing() {
    state.putTask("ABC-1", alphaTask("ABC-1", TaskStatus.IN_PROGRESS).build());
    Page page = openRoleLog("master", "{\"lines\":[],\"next\":0}");

    page.locator("#filter").fill("nothing matches this");

    assertThat(page.locator("#phases")).isHidden();
  }

  @Test
  void refusalIsDangerWhileMoveAndNoteKeepQuietColours() {
    Page page =
        openRoleLog(
            "master",
            "{\"lines\":[\"2026-11-25 09:00:01Z ERROR refused ABC-42\","
                + "\"2026-11-25 09:00:02Z INFO moved ABC-42\","
                + "\"2026-11-25 09:00:03Z INFO noted ABC-42\"],\"next\":3}");

    assertThat(page.locator("#pane.role-log .log-line.danger")).hasCount(1);
    assertThat(page.locator("#pane.role-log .log-line.danger")).hasCSS("color", "rgb(180, 35, 24)");
    assertThat(page.locator("#pane.role-log .log-line:not(.danger)").first())
        .hasCSS("color", "rgb(28, 32, 36)");
    assertThat(page.locator("#pane.role-log .log-line:not(.danger) .log-level").first())
        .hasCSS("color", "rgb(95, 102, 114)");
    assertThat(page.locator("#pane.role-log .log-line .log-time").first())
        .hasCSS("color", "rgb(95, 102, 114)");
  }

  @Test
  void helpHasRowsForTheMutedTimeAndForDangerEntries() {
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/");
    page.locator("#reports-toggle").click();
    page.locator("#show-help").click();

    assertThat(page.locator("#report-section .legend .log-line .log-time")).hasCount(2);
    assertThat(page.locator("#report-section .legend .log-line.danger")).hasCount(1);
  }
}
