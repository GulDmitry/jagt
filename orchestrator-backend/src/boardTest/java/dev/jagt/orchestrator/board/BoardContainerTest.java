package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BoardBrowser.class)
class BoardContainerTest {

  @Test
  void boardOpensLiveAfterPasswordLoginWhenItRunsInsideContainer(Browser browser) {
    String url = System.getenv("JAGT_BOARD_URL");
    Assumptions.assumeTrue(url != null, "JAGT_BOARD_URL is unset"); // skipped without a board
    Page page = browser.newContext().newPage();

    page.navigate(url);
    page.fill("input[name=password]", Objects.requireNonNull(System.getenv("JAGT_BOARD_PASSWORD")));
    page.click("button.primary");

    assertThat(page.locator("#gear")).isVisible();
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
  }

  @Test
  void logOutInTheGearEndsTheSessionAndShowsTheLoginPage(Browser browser) {
    String url = System.getenv("JAGT_BOARD_URL");
    Assumptions.assumeTrue(url != null, "JAGT_BOARD_URL is unset"); // skipped without a board
    Page page = browser.newContext().newPage();
    page.navigate(url);
    page.fill("input[name=password]", Objects.requireNonNull(System.getenv("JAGT_BOARD_PASSWORD")));
    page.click("button.primary");
    assertThat(page.locator("body")).not().hasClass(Pattern.compile("\\bstale\\b"));
    page.click("#gear");

    page.click("#gear-logout");

    assertThat(page.locator("input[name=password]")).isVisible();
  }
}
