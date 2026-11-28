package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardStyleguideTest extends BoardPageContext {

  private static final String LIGHT = "[data-theme=light] figure.state";
  private static final String DARK = "[data-theme=dark] figure.state";
  private static final Pattern SELECTOR = Pattern.compile("([^{};]+)\\{");
  private static final Pattern CLASS = Pattern.compile("\\.(-?[A-Za-z_][\\w-]*)");
  private static final String SHOWN_IN =
      "theme => [...new Set([...document.querySelectorAll(`.theme[data-theme=${theme}] *`)]"
          + ".flatMap(each => [...each.classList]))].join(' ')";
  private static final String VERB_CLASSES =
      "() => JSON.stringify(Object.entries(Object.groupBy("
          + "[...document.querySelectorAll('button[data-action]')],"
          + " each => `${each.dataset.action} ${each.dataset.place ?? 'card'}`))"
          + ".map(([verb, buttons]) => [verb, [...new Set(buttons.map(each => [...each.classList]"
          + ".filter(name => !['primary', 'again', 'quiet'].includes(name)).sort().join(' ')))]])"
          + ".filter(([, designs]) => designs.length > 1))";

  @Autowired
  BoardStyleguideTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void theStyleguideNamesEveryStateTheDesignCodeNeeds() {
    Page page = session.newPage();

    page.navigate("http://localhost:" + port + "/styleguide.html");
    assertThat(page.locator("figure.state").first()).isVisible();

    org.assertj.core.api.Assertions.assertThat(
            page.locator("figure.state > figcaption").allTextContents())
        .contains(
            "stopped: nothing polls again",
            "across repositories, the poll stopped",
            "live on a shared branch",
            "live, no deploy verb on the card",
            "the deploy highlighted, its last run still live",
            "the last sweep could not read the checks",
            "broken: checks failed",
            "a press in flight",
            "the keys line under the list",
            "the open task, its session has no window",
            "the open task, its session shows a blank screen",
            "a round drafted: two blocks, a quote, the three verdicts");
  }

  @Test
  void theStyleguideDrawsEveryComponentInEachOfItsStatesWithoutAnError() {
    Page page = session.newPage();
    List<String> errors = new ArrayList<>();
    page.onPageError(errors::add);

    page.navigate("http://localhost:" + port + "/styleguide.html");

    assertThat(page.locator(LIGHT + " > article")).hasCount(51);
    assertThat(page.locator(DARK + " > article")).hasCount(51);
    assertThat(page.locator(LIGHT + " #tasks")).hasCount(4);
    assertThat(page.locator("figure[data-state='a row with no row verb'] [data-place=row]"))
        .hasCount(0);
    assertThat(page.locator(LIGHT + " #pane article")).hasCount(9);
    assertThat(page.locator(LIGHT + " .round .block")).hasCount(3);
    assertThat(page.locator(LIGHT + " .round .quote")).hasCount(1);
    assertThat(page.locator(LIGHT + " .round .verdict.ok")).hasText("FIXED");
    assertThat(page.locator(LIGHT + " .round .verdict.you")).hasText("QUESTION");
    assertThat(page.locator(LIGHT + " .round .verdict.muted")).hasText("PUSHED BACK");
    assertThat(page.locator("[data-theme=light] section[aria-label=Jobs] table.jobs")).hasCount(3);
    assertThat(page.locator(LIGHT + " .checks-list li")).hasCount(3);
    org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
  }

  @Test
  void everyStyledClassIsShownInBothThemes() throws IOException {
    String css = Files.readString(Path.of("../board/app.css")).replaceAll("(?s)/\\*.*?\\*/", "");
    List<String> defined =
        SELECTOR
            .matcher(css)
            .results()
            .map(selector -> selector.group(1).trim())
            .filter(selector -> !selector.matches("(@|from\\b|to\\b|\\d).*"))
            .flatMap(selector -> CLASS.matcher(selector).results().map(m -> m.group(1)))
            .distinct()
            .toList();
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/styleguide.html");
    assertThat(page.locator(".legend-key").first()).isAttached();

    List<String> light = List.of(String.valueOf(page.evaluate(SHOWN_IN, "light")).split(" "));
    List<String> dark = List.of(String.valueOf(page.evaluate(SHOWN_IN, "dark")).split(" "));

    org.assertj.core.api.Assertions.assertThat(defined)
        .as("classes no light frame shows")
        .isSubsetOf(light);
    org.assertj.core.api.Assertions.assertThat(defined)
        .as("classes no dark frame shows")
        .isSubsetOf(dark);
  }

  @Test
  void everyVerbWearsOneDesignWhereverTheStyleguideDrawsIt() {
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + "/styleguide.html");
    assertThat(page.locator("figure.state > article").first()).isVisible();

    Object twoDesigns = page.evaluate(VERB_CLASSES);

    org.assertj.core.api.Assertions.assertThat(twoDesigns).isEqualTo("[]");
  }
}
