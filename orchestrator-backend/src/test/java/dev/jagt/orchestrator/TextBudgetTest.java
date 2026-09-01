package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TextBudgetTest {

    @ParameterizedTest
    @CsvSource({"../README.md, 1000", "../AGENTS.md, 1600", "../ARCHITECTURE.md, 2900",
            "../USE-CASES.md, 4600", "../TODO.md, 600"})
    void aDocumentStaysInsideItsWordBudget(Path document, int maxWords) {
        assertThat(words(document)).describedAs("%s", document).isLessThanOrEqualTo(maxWords);
    }

    @Test
    void aRuleIsStatedInSevenHundredWordsOrItIsBeingArgued() {
        assertThat(markdownIn(Path.of("../docs/rules"))).allSatisfy(rule ->
                assertThat(words(rule)).describedAs("%s", rule).isLessThanOrEqualTo(700));
    }

    @Test
    void aGuideStaysUnderTwelveHundredWords() {
        assertThat(markdownIn(Path.of("../docs"))).allSatisfy(guide ->
                assertThat(words(guide)).describedAs("%s", guide).isLessThanOrEqualTo(1200));
    }

    @ParameterizedTest
    @CsvSource({"../README.md", "../AGENTS.md", "../ARCHITECTURE.md", "../USE-CASES.md", "../TODO.md"})
    void noParagraphHidesInATableCell(Path document) {
        assertThat(lines(document)).describedAs("%s", document)
                .allSatisfy(line -> assertThat(line.length()).describedAs("%s", line).isLessThanOrEqualTo(220));
    }

    @Test
    void noParagraphHidesInARulesTableCell() {
        assertThat(markdownIn(Path.of("../docs/rules")).stream()
                .flatMap(rule -> lines(rule).stream())
                .filter(line -> line.length() > 220)).isEmpty();
    }

    @Test
    void commentsAreAtMostFifteenPercentOfTheMainSources() {
        List<String> code = mainSources().flatMap(source -> lines(source).stream())
                .map(String::strip).filter(line -> !line.isEmpty()).toList();
        long comments = code.stream().filter(line -> line.startsWith("//")
                || line.startsWith("/*") || line.startsWith("*")).count();

        assertThat(100 * comments / code.size())
                .describedAs("%d comment lines in %d", comments, code.size()).isLessThanOrEqualTo(15);
    }

    @Test
    void readsEveryFileItClaimsToCheck() {
        assertThat(markdownIn(Path.of("../docs/rules"))).hasSizeGreaterThan(5);
        assertThat(markdownIn(Path.of("../docs"))).hasSizeGreaterThan(3);
        assertThat(mainSources().count()).isGreaterThan(100);
    }

    private static int words(Path document) {
        return lines(document).stream().mapToInt(line -> line.isBlank() ? 0 : line.trim().split("\\s+").length)
                .sum();
    }

    private static List<Path> markdownIn(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".md")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<Path> mainSources() {
        return Stream.of("core", "usecase", "adapter", "surface", ".")
                .map(module -> Path.of(module, "src/main/java"))
                .flatMap(TextBudgetTest::javaFilesUnder);
    }

    private static Stream<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
