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

    private static final Pattern ANCHORED_LINK = Pattern.compile("]\\(([^)\\s#]*)#([^)\\s]+)[^)]*\\)");

    @Test
    void everyRelativeLinkReachesAFile() {
        List<String> broken = documents().stream()
                .flatMap(document -> LINK.matcher(withoutCodeBlocks(document)).results()
                        .map(link -> link.group(1))
                        .filter(target -> !target.matches("[a-z]+:.*"))
                        .filter(target -> !Files.exists(document.resolveSibling(target)))
                        .map(target -> document + " -> " + target))
                .toList();

        assertThat(broken).isEmpty();
    }

    @Test
    void everyAnchoredLinkReachesAHeading() {
        List<String> broken = documents().stream()
                .flatMap(document -> ANCHORED_LINK.matcher(withoutCodeBlocks(document)).results()
                        .filter(link -> !link.group(1).matches("[a-z]+:.*"))
                        .filter(link -> !headingSlugs(document.resolveSibling(link.group(1).isEmpty()
                                ? document.getFileName().toString() : link.group(1))).contains(link.group(2)))
                        .map(link -> document + " -> " + link.group(1) + "#" + link.group(2)))
                .toList();

        assertThat(broken).isEmpty();
    }

    @Test
    void decisionIndexListsEveryRecordAndNothingElse() {
        Path directory = Path.of("../docs/decisions");
        List<String> records = namesIn(directory).stream().filter(name -> name.matches("\\d{4}-.*")).sorted().toList();
        List<String> indexed = Pattern.compile("]\\((\\d{4}-[^)#]*\\.md)").matcher(withoutCodeBlocks(directory.resolve("README.md")))
                .results().map(link -> link.group(1)).sorted().toList();

        assertThat(indexed).isEqualTo(records);
    }

    @Test
    void noOpenChangeSitsOnMain() throws IOException {
        try (Stream<Path> changes = Files.list(Path.of("../openspec/changes"))) {
            assertThat(changes.map(path -> path.getFileName().toString()).filter(name -> !name.equals("archive")))
                    .isEmpty();
        }
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

    private static List<Path> documents() {
        return Stream.concat(Stream.of("..", "../docs", "../docs/rules", "../.claude/rules")
                .flatMap(directory -> markdownIn(Path.of(directory)).stream()), specs().stream()).toList();
    }

    private static List<String> headingSlugs(Path document) {
        if (!Files.isRegularFile(document)) {
            return List.of();
        }
        return withoutCodeBlocks(document).lines().filter(line -> line.matches("#{1,6} .*"))
                .map(line -> line.replaceFirst("#+ ", "").trim().toLowerCase()
                        .replaceAll("[^\\p{L}\\p{N} _-]", "").replace(' ', '-'))
                .toList();
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
