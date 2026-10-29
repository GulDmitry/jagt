package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class BoardStyleguideTest extends BoardPageContext {

    @Test
    void theStyleguideNamesEveryStateTheDesignCodeNeeds() {
        Page page = session.newPage();

        page.navigate("http://localhost:" + port + "/styleguide.html");
        assertThat(page.locator("figure.state").first()).isVisible();

        org.assertj.core.api.Assertions.assertThat(page.locator("figure.state > figcaption").allTextContents())
                .contains("stopped: nothing polls again", "across repositories, the poll stopped",
                        "live on a shared branch", "live, no deploy verb on the card",
                        "the deploy highlighted, its last run still live", "the last sweep could not read the checks",
                        "broken: checks failed", "a press in flight", "the keys line under the list",
                        "the open task, its session has no window", "the open task, its session shows a blank screen",
                        "a round drafted: two blocks, a quote, the three verdicts");
    }

    @Test
    void theStyleguideDrawsEveryComponentInEachOfItsStatesWithoutAnError() {
        Page page = session.newPage();
        List<String> errors = new ArrayList<>();
        page.onPageError(errors::add);

        page.navigate("http://localhost:" + port + "/styleguide.html");

        assertThat(page.locator("figure.state > article")).hasCount(20);
        assertThat(page.locator("figure.state #tasks")).hasCount(3);
        assertThat(page.locator("figure.state #pane article")).hasCount(6);
        assertThat(page.locator("figure.state .round .block")).hasCount(3);
        assertThat(page.locator("figure.state .round .quote")).hasCount(1);
        assertThat(page.locator("figure.state .round .verdict.ok")).hasText("FIXED");
        assertThat(page.locator("figure.state .round .verdict.you")).hasText("QUESTION");
        assertThat(page.locator("figure.state .round .verdict.muted")).hasText("PUSHED BACK");
        assertThat(page.locator("figure.state table.jobs")).hasCount(3);
        assertThat(page.locator("figure.state .checks-list li")).hasCount(3);
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
    }
}
