package dev.jagt.orchestrator.board;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class BoardStyleguideTest extends BoardPageContext {

    @Test
    void theStyleguideDrawsEveryComponentInEachOfItsStatesWithoutAnError() {
        Page page = session.newPage();
        List<String> errors = new ArrayList<>();
        page.onPageError(errors::add);

        page.navigate("http://localhost:" + port + "/styleguide.html");

        assertThat(page.locator("figure.state")).hasCount(19);
        assertThat(page.locator("figure.state > article")).hasCount(12);
        assertThat(page.locator("figure.state #tasks")).hasCount(2);
        assertThat(page.locator("figure.state #pane article")).hasCount(3);
        assertThat(page.locator("figure.state table.jobs")).hasCount(3);
        assertThat(page.locator("figure.state .checks-list li")).hasCount(3);
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
    }
}
