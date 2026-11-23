package dev.jagt.orchestrator.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

class BoardAccessibilityTest extends BoardPageContext {

  @Autowired
  BoardAccessibilityTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @ParameterizedTest(name = "{0} after pressing {1}")
  @CsvSource(
      nullValues = "none",
      value = {"/,none", "/,t", "/,Control+k", "/styleguide.html,none", "/login.html,none"})
  void everyViewHasNoSeriousOrCriticalViolation(String path, String key) {
    putReviewTask("ABC-1");
    Page page = session.newPage();
    page.navigate("http://localhost:" + port + path);
    page.waitForLoadState();
    if (key != null) {
      page.keyboard().press(key);
    }

    List<Rule> violations = new AxeBuilder(page).analyze().getViolations();

    assertThat(
            violations.stream()
                .filter(rule -> Set.of("serious", "critical").contains(rule.getImpact()))
                .map(rule ->
                    rule.getId()
                        + " on "
                        + rule.getNodes().stream().map(node -> node.getTarget()).toList())
                .toList())
        .as("serious or critical violations on %s after %s", path, key)
        .isEmpty();
  }

  @Test
  void settingsDialogHasNoSeriousOrCriticalViolation() {
    Page page = openLiveBoard();
    page.locator("#gear").click();
    page.locator("#gear-settings").click();
    page.locator("#settings-dialog").waitFor();

    List<String> serious =
        new AxeBuilder(page)
            .analyze().getViolations().stream()
                .filter(rule -> Set.of("serious", "critical").contains(rule.getImpact()))
                .map(rule ->
                    rule.getId()
                        + " on "
                        + rule.getNodes().stream().map(n -> n.getTarget()).toList())
                .toList();

    assertThat(serious).as("serious or critical violations in settings").isEmpty();
  }
}
