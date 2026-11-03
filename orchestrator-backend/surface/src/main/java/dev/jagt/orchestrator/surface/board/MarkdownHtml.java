package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.port.LineChange;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.node.SourceSpan;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Markdown as HTML that runs no script and fetches nothing: raw HTML is escaped, a link keeps a safe scheme, an image is its alt text. */
final class MarkdownHtml {

    private static final List<Extension> TABLES = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(TABLES)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();

    private MarkdownHtml() {
    }

    static String render(String markdown) {
        return render(markdown, LineChange.none());
    }

    static String render(String markdown, LineChange change) {
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
        Set<Node> removed = new HashSet<>();
        change.removed().forEach((line, gone) -> showRemoved(document, line, gone, removed));
        return HtmlRenderer.builder().extensions(TABLES).escapeHtml(true).sanitizeUrls(true)
                .attributeProviderFactory(context -> (node, tag, attributes) -> {
                    if (removed.contains(node)) {
                        attributes.put("class", "removed");
                    } else if (!tag.equals("code") && marked(node, change)) {
                        attributes.put("class", "changed");
                    }
                }).build().render(document);
    }

    private static void showRemoved(Node document, int line, List<String> gone,
                                    Set<Node> removed) {
        List<String> shown = gone.stream().filter(text -> !text.isBlank()).toList();
        Node before = document.getFirstChild();
        while (before != null && lastLine(before) < line) {
            before = before.getNext();
        }
        for (String text : shown) {
            Paragraph struck = new Paragraph();
            struck.appendChild(new Text(text));
            removed.add(struck);
            if (before == null) {
                document.appendChild(struck);
            } else {
                before.insertBefore(struck);
            }
        }
    }

    private static int lastLine(Node block) {
        return block.getSourceSpans().stream().mapToInt(SourceSpan::getLineIndex).max().orElse(Integer.MAX_VALUE);
    }

    private static boolean marked(Node node, LineChange change) {
        boolean block = node instanceof Heading || node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock
                || node instanceof TableRow || node instanceof ListItem
                || node instanceof Paragraph && !(node.getParent() instanceof ListItem);
        Node own = node instanceof ListItem ? node.getFirstChild() : node;
        return block && own != null && own.getSourceSpans().stream()
                .anyMatch(span -> change.changed().contains(span.getLineIndex()));
    }
}
