package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.surface.ui.log.RoleFeeds.RoleFeedWritten;
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
                            ? "{\"lines\":[\"first step\"],\"next\":1}"
                            : "{\"lines\":[\"second step\"],\"next\":2}")));
    page.navigate("http://localhost:" + port + "/");

    page.locator("#role-logs button.chip").click();

    assertThat(page.locator("#pane.role-log pre")).hasText("first step");
    assertThat(page.locator("#role-logs button.chip")).hasAttribute("aria-pressed", "true");

    events.publishEvent(new RoleFeedWritten("master"));

    assertThat(page.locator("#pane.role-log pre")).hasText("first step\nsecond step");

    page.locator("#role-logs button.chip").click();

    assertThat(page.locator("#pane.role-log")).hasCount(0);
    assertThat(page.locator("#role-logs button.chip")).hasAttribute("aria-pressed", "false");
  }
}
