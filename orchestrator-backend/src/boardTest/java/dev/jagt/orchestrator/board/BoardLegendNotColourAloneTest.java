package dev.jagt.orchestrator.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardLegendNotColourAloneTest extends BoardPageContext {

  private static final String MARKS_SHARING_ONE_LOOK =
      """
      () => [...document.querySelectorAll('#report-section .legend .legend-key')].flatMap(row => {
        const look = mark => {
          const style = getComputedStyle(mark);
          return [
            mark.tagName,
            mark.textContent.trim(),
            style.borderRadius,
            style.borderTopStyle,
            style.width,
            style.height,
            style.backgroundColor === 'rgba(0, 0, 0, 0)',
            getComputedStyle(mark, '::before').content,
            getComputedStyle(mark, '::after').content
          ].join('|');
        };
        const seen = new Set();
        return [...row.children]
          .filter(mark => !mark.classList.contains('legend-edge'))
          .map(look)
          .filter(one => seen.size === seen.add(one).size);
      })
      """;

  @Autowired
  BoardLegendNotColourAloneTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void everyPairOfMarksInOneLegendRowDiffersInShapeOrGlyph() {
    Page page = openLiveBoard();
    page.locator("#reports-toggle").click();
    page.locator("#show-help").click();
    page.locator("#report-section .legend").waitFor();

    Object sharingOneLook = page.evaluate(MARKS_SHARING_ONE_LOOK);

    assertThat((List<?>) sharingOneLook).isEmpty();
  }
}
