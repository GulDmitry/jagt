package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TextBudgetTest {

    private static final Pattern NOT_PROSE = Pattern.compile("^(```|\\||#|<|>)");
    private static final Pattern LIST_ITEM = Pattern.compile("^([-*]|\\d+\\.)\\s+");
    private static final Path SESSION_BRIEF = Path.of("../session-brief.md.dist");
    private static final Pattern STEP = Pattern.compile("(?m)^## [a-z][a-z0-9-]*$");
    private static final Path MAP_FILE = Path.of("../ARCHITECTURE.md");
    private static final int RECORD = 50;
    private static final int RULE = 100;
    private static final int PAGE = 200;
    private static final int MAP = 500;
    private static final int WIDTH = 150;
    private static final String PROMPT_RULE = " — a prompt: compress it, then run promptEval and masterEval"
            + " (docs/rules/testing.md)";

    @Test
    void aDecisionIsOneRecordOfFiftyLines() {
        assertThat(decisions())
                .allSatisfy(record -> assertThat(lines(record)).describedAs("%s", record).hasSizeLessThanOrEqualTo(RECORD));
    }

    @Test
    void aRuleIsStatedInAHundredLinesOrItIsBeingArgued() {
        assertThat(markdownIn(Path.of("../docs/rules"))).allSatisfy(rule ->
                assertThat(lines(rule)).describedAs("%s", rule).hasSizeLessThanOrEqualTo(RULE));
    }

    @Test
    void aPageReadWholeStaysUnderTwoHundredLines() {
        assertThat(pages()).allSatisfy(page ->
                assertThat(lines(page)).describedAs("%s%s", page, prompt(page)).hasSizeLessThanOrEqualTo(PAGE));
    }

    @Test
    void aStepOfTheSessionBriefStaysUnderTwoHundredLines() {
        assertThat(STEP.split(String.join("\n", lines(SESSION_BRIEF)))).allSatisfy(step ->
                assertThat(step.lines().count()).describedAs("%s%s", step.lines().findFirst().orElse(""), PROMPT_RULE)
                        .isLessThanOrEqualTo(PAGE));
    }

    @Test
    void theMapReadInPartsStaysUnderFiveHundredLines() {
        assertThat(lines(MAP_FILE)).hasSizeLessThanOrEqualTo(MAP);
    }

    @Test
    void noLineOutsideATableRunsPastOneHundredFiftyCharacters() {
        assertThat(Stream.of(pages(), decisions(), markdownIn(Path.of("../docs/rules")), List.of(MAP_FILE, SESSION_BRIEF))
                .flatMap(List::stream)
                .flatMap(text -> lines(text).stream().filter(line -> line.length() > WIDTH && !line.startsWith("|"))
                        .map(line -> text.getFileName() + ": " + line))).isEmpty();
    }

    @Test
    void noSentenceAModelOrAHumanFollowsRunsPastTwentyFiveWords() {
        List<Path> read = Stream.of(markdownIn(Path.of("../docs/rules")), specs(), List.of(Path.of("../AGENTS.md")),
                List.of(SESSION_BRIEF)).flatMap(List::stream).toList();

        assertThat(read.stream().flatMap(document -> sentences(document).stream()
                .filter(sentence -> sentence.split("\\s+").length > 25)
                .map(sentence -> document.getFileName() + ": " + sentence + prompt(document)))).isEmpty();
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
        assertThat(markdownIn(Path.of("../docs/rules"))).hasSizeGreaterThan(3);
        assertThat(markdownIn(Path.of("../docs"))).hasSizeGreaterThan(3);
        assertThat(markdownIn(Path.of("../docs/decisions"))).hasSizeGreaterThan(1);
        assertThat(SESSION_BRIEF).isRegularFile();
        assertThat(specs()).hasSizeGreaterThan(5);
        assertThat(mainSources().count()).isGreaterThan(100);
    }

    private static String prompt(Path document) {
        return document.equals(SESSION_BRIEF) ? PROMPT_RULE : "";
    }

    private static List<Path> pages() {
        return Stream.of(List.of(Path.of("../AGENTS.md"), Path.of("../README.md"), Path.of("../docs/decisions/README.md")),
                markdownIn(Path.of("../docs")), specs()).flatMap(List::stream).toList();
    }

    private static List<Path> decisions() {
        return markdownIn(Path.of("../docs/decisions")).stream()
                .filter(path -> path.getFileName().toString().matches("\\d{4}-.*")).toList();
    }

    private static List<String> sentences(Path document) {
        List<String> blocks = new ArrayList<>();
        StringBuilder block = new StringBuilder();
        boolean fenced = false;
        for (String line : lines(document)) {
            String text = line.strip();
            boolean fence = text.startsWith("```");
            fenced ^= fence;
            boolean prose = !fenced && !fence && !text.isEmpty() && !NOT_PROSE.matcher(text).find();
            if (!prose || LIST_ITEM.matcher(text).find()) {
                blocks.add(block.toString());
                block.setLength(0);
            }
            if (prose) {
                block.append(' ').append(LIST_ITEM.matcher(text).replaceFirst(""));
            }
        }
        blocks.add(block.toString());
        return blocks.stream()
                .map(text -> text.replaceAll("`[^`]*`", "CODE").replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1"))
                .flatMap(text -> Arrays.stream(text.split("(?<=[.!?;:])\\s+| · ")))
                .map(String::strip).filter(sentence -> !sentence.isEmpty()).toList();
    }

    private static List<Path> markdownIn(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".md")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> specs() {
        try (Stream<Path> capabilities = Files.list(Path.of("../openspec/specs"))) {
            return capabilities.map(capability -> capability.resolve("spec.md")).filter(Files::isRegularFile).toList();
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
