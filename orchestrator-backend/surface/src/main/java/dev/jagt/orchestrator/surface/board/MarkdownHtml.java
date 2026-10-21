package dev.jagt.orchestrator.surface.board;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.Extension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/** Markdown as HTML that cannot run script: raw HTML is escaped and a link keeps only a safe scheme. */
final class MarkdownHtml {

    private static final List<Extension> TABLES = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(TABLES).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(TABLES)
            .escapeHtml(true).sanitizeUrls(true).build();

    private MarkdownHtml() {
    }

    static String render(String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }
}
