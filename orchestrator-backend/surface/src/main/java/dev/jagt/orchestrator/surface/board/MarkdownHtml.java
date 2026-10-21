package dev.jagt.orchestrator.surface.board;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/** Markdown as HTML that runs no script and fetches nothing: raw HTML is escaped, a link keeps a safe scheme, an image is its alt text. */
final class MarkdownHtml {

    private static final List<Extension> TABLES = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(TABLES).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(TABLES)
            .escapeHtml(true).sanitizeUrls(true).build();

    private MarkdownHtml() {
    }

    static String render(String markdown) {
        Node document = PARSER.parse(markdown);
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                StringBuilder alt = new StringBuilder();
                image.accept(new AbstractVisitor() {
                    @Override
                    public void visit(Text text) {
                        alt.append(text.getLiteral());
                    }
                });
                image.insertBefore(new Text(alt.toString()));
                image.unlink();
            }
        });
        return RENDERER.render(document);
    }
}
