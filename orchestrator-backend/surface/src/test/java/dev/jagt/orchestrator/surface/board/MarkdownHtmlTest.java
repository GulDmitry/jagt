package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.LineChange;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarkdownHtmlTest {

  @Test
  void anAgentsRawScriptComesBackEscapedSoItCannotRunOnTheBoard() {
    String html =
        MarkdownHtml.render("<script>alert(1)</script>\n\ntext <img src=x onerror=alert(1)>");

    assertThat(html).doesNotContain("<script").doesNotContain("<img").contains("&lt;script&gt;");
  }

  @Test
  void aJavascriptLinkLosesItsTarget() {
    String html = MarkdownHtml.render("[open](javascript:alert(1))");

    assertThat(html).doesNotContain("javascript:");
  }

  @Test
  void anImageIsItsAltTextSoTheBoardFetchesNothing() {
    String html =
        MarkdownHtml.render("before ![the diagram](https://example.com/beacon.png) after");

    assertThat(html)
        .doesNotContain("<img")
        .doesNotContain("example.com")
        .contains("before the diagram after");
  }

  @Test
  void aMermaidFenceKeepsItsLanguageMarkerAndEscapedBodyForThePageToDraw() {
    String html = MarkdownHtml.render("```mermaid\nsequenceDiagram\nA->>B: <go>\n```");

    assertThat(html)
        .contains("<code class=\"language-mermaid\">")
        .contains("A-&gt;&gt;B: &lt;go&gt;");
  }

  @Test
  void aMarkdownTableIsRenderedAsATable() {
    String html = MarkdownHtml.render("| a | b |\n|---|---|\n| 1 | 2 |");

    assertThat(html).contains("<table>").contains("<td>1</td>");
  }

  @Test
  void aChangedParagraphIsMarkedAndAnUnchangedOneIsNot() {
    String html =
        MarkdownHtml.render(
            "# Plan\n\nkeep this\n\nnew words\n",
            new LineChange(Set.of(4), Map.of(4, List.of("old words"))));

    assertThat(html).contains("<p class=\"changed\">new words</p>").contains("<p>keep this</p>");
  }

  @Test
  void aRemovedLineIsShownStruckBeforeTheLineItStoodBefore() {
    String html =
        MarkdownHtml.render(
            "# Plan\n\nship\n", new LineChange(Set.of(), Map.of(2, List.of("retry once", ""))));

    assertThat(html).contains("<p class=\"removed\">retry once</p>\n<p>ship</p>");
  }

  @Test
  void aChangedListItemAndATableRowAreMarked() {
    String html =
        MarkdownHtml.render(
            "- a\n- b2\n\n| x | y |\n|---|---|\n| 1 | 9 |\n",
            new LineChange(Set.of(1, 5), Map.of()));

    assertThat(html)
        .contains("<li class=\"changed\">b2</li>")
        .contains("<li>a</li>")
        .contains("<tr class=\"changed\">");
  }

  @Test
  void aRemovedLineIsShownEscapedLikeAnyText() {
    String html =
        MarkdownHtml.render(
            "ship\n", new LineChange(Set.of(), Map.of(0, List.of("<script>x</script>"))));

    assertThat(html).doesNotContain("<script").contains("&lt;script&gt;");
  }

  @Test
  void aChangedHeadingIsMarked() {
    String html = MarkdownHtml.render("# Plan v2\n", new LineChange(Set.of(0), Map.of()));

    assertThat(html).contains("<h1 class=\"changed\">Plan v2</h1>");
  }

  @Test
  void aChangedFencedCodeBlockIsMarked() {
    String html = MarkdownHtml.render("```\nrun it\n```\n", new LineChange(Set.of(1), Map.of()));

    assertThat(html).contains("<pre class=\"changed\">");
  }

  @Test
  void aChangedIndentedCodeBlockIsMarked() {
    String html = MarkdownHtml.render("    run it\n", new LineChange(Set.of(0), Map.of()));

    assertThat(html).contains("<pre class=\"changed\">");
  }

  @Test
  void aLineRemovedFromTheEndIsShownStruckAfterTheLastLine() {
    String html =
        MarkdownHtml.render("ship\n", new LineChange(Set.of(), Map.of(1, List.of("announce"))));

    assertThat(html).contains("<p>ship</p>\n<p class=\"removed\">announce</p>");
  }
}
