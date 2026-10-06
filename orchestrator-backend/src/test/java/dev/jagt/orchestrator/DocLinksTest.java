package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DocLinksTest {

    private static final Pattern LINK = Pattern.compile("]\\(([^)\\s#]+)[^)]*\\)");

    @Test
    void everyRelativeLinkReachesAFile() {
        List<String> broken = Stream.concat(Stream.of("..", "../docs", "../docs/rules", "../.claude/rules")
                        .flatMap(directory -> markdownIn(Path.of(directory)).stream()), specs().stream())
                .flatMap(document -> LINK.matcher(withoutCodeBlocks(document)).results()
                        .map(link -> link.group(1))
                        .filter(target -> !target.matches("[a-z]+:.*"))
                        .filter(target -> !Files.exists(document.resolveSibling(target)))
                        .map(target -> document + " -> " + target))
                .toList();

        assertThat(broken).isEmpty();
    }

    @Test
    void everyRuleHasAPathScopedPointer() {
        assertThat(namesIn(Path.of("../.claude/rules"))).containsAll(namesIn(Path.of("../docs/rules")));
    }

    @Test
    void everyPointerNamesARuleOrASpec() {
        List<String> sources = Stream.concat(namesIn(Path.of("../docs/rules")).stream(),
                specs().stream().map(spec -> spec.getParent().getFileName() + ".md")).toList();

        assertThat(namesIn(Path.of("../.claude/rules"))).isSubsetOf(sources);
    }

    private static List<Path> specs() {
        try (Stream<Path> capabilities = Files.list(Path.of("../openspec/specs"))) {
            return capabilities.map(capability -> capability.resolve("spec.md")).filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> namesIn(Path directory) {
        return markdownIn(directory).stream().map(path -> path.getFileName().toString()).toList();
    }

    private static String withoutCodeBlocks(Path document) {
        try {
            return Files.readString(document).replaceAll("(?s)```.*?```", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> markdownIn(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".md")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
