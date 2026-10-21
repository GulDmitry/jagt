package dev.jagt.orchestrator.surface.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownHtmlTest {

    @Test
    void anAgentsRawScriptComesBackEscapedSoItCannotRunOnTheBoard() {
        String html = MarkdownHtml.render("<script>alert(1)</script>\n\ntext <img src=x onerror=alert(1)>");

        assertThat(html).doesNotContain("<script").doesNotContain("<img").contains("&lt;script&gt;");
    }

    @Test
    void aJavascriptLinkLosesItsTarget() {
        String html = MarkdownHtml.render("[open](javascript:alert(1))");

        assertThat(html).doesNotContain("javascript:");
    }

    @Test
    void anImageIsItsAltTextSoTheBoardFetchesNothing() {
        String html = MarkdownHtml.render("before ![the diagram](https://example.com/beacon.png) after");

        assertThat(html).doesNotContain("<img").doesNotContain("example.com").contains("before the diagram after");
    }

    @Test
    void aMermaidFenceKeepsItsLanguageMarkerAndEscapedBodyForThePageToDraw() {
        String html = MarkdownHtml.render("```mermaid\nsequenceDiagram\nA->>B: <go>\n```");

        assertThat(html).contains("<code class=\"language-mermaid\">").contains("A-&gt;&gt;B: &lt;go&gt;");
    }

    @Test
    void aMarkdownTableIsRenderedAsATable() {
        String html = MarkdownHtml.render("| a | b |\n|---|---|\n| 1 | 2 |");

        assertThat(html).contains("<table>").contains("<td>1</td>");
    }
}
