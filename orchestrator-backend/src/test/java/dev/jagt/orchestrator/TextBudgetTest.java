package dev.jagt.orchestrator;

import static dev.jagt.orchestrator.DocFiles.markdownIn;
import static dev.jagt.orchestrator.DocFiles.specs;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TextBudgetTest {

  private static final Pattern STEP = Pattern.compile("(?m)^## [a-z][a-z0-9-]*$");
  private static final Path MAP_FILE = Path.of("../ARCHITECTURE.md");
  private static final int RECORD = 50;
  private static final int RULE = 100;
  private static final int PAGE = 200;
  private static final int MAP = 500;
  private static final int WIDTH = 150;

  @Test
  void decisionIsOneRecordOfFiftyLines() {
    assertThat(DecisionRecords.records())
        .allSatisfy(record ->
            assertThat(DocFiles.read(record).lines().toList())
                .describedAs("%s", record)
                .hasSizeLessThanOrEqualTo(RECORD));
  }

  @Test
  void ruleIsStatedInHundredLinesOrItIsBeingArgued() {
    assertThat(markdownIn(Path.of("../docs/rules")))
        .allSatisfy(rule ->
            assertThat(DocFiles.read(rule).lines().toList())
                .describedAs("%s", rule)
                .hasSizeLessThanOrEqualTo(RULE));
  }

  @Test
  void pageReadWholeStaysUnderTwoHundredLines() {
    assertThat(BudgetedTexts.pages())
        .allSatisfy(page ->
            assertThat(DocFiles.read(page).lines().toList())
                .describedAs("%s%s", page, BudgetedTexts.prompt(page))
                .hasSizeLessThanOrEqualTo(PAGE));
  }

  @Test
  void stepOfTheSessionBriefStaysUnderTwoHundredLines() {
    assertThat(
            STEP.split(
                String.join("\n", DocFiles.read(BudgetedTexts.SESSION_BRIEF).lines().toList())))
        .allSatisfy(step ->
            assertThat(step.lines().count())
                .describedAs("%s%s", step.lines().findFirst().orElse(""), BudgetedTexts.PROMPT_RULE)
                .isLessThanOrEqualTo(PAGE));
  }

  @Test
  void theMapReadInPartsStaysUnderFiveHundredLines() {
    assertThat(DocFiles.read(MAP_FILE).lines().toList()).hasSizeLessThanOrEqualTo(MAP);
  }

  @Test
  void noLineOutsideTableRunsPastOneHundredFiftyCharacters() {
    assertThat(
            Stream.of(
                    BudgetedTexts.pages(),
                    DecisionRecords.records(),
                    markdownIn(Path.of("../docs/rules")),
                    List.of(MAP_FILE, BudgetedTexts.SESSION_BRIEF))
                .flatMap(List::stream)
                .flatMap(text ->
                    DocFiles.read(text).lines().toList().stream()
                        .filter(line -> line.length() > WIDTH && !line.startsWith("|"))
                        .map(line -> text.getFileName() + ": " + line)))
        .isEmpty();
  }

  @Test
  void noSentenceModelOrHumanFollowsRunsPastTwentyFiveWords() {
    List<Path> read =
        Stream.of(
                markdownIn(Path.of("../docs/rules")),
                specs(),
                List.of(Path.of("../AGENTS.md")),
                List.of(BudgetedTexts.SESSION_BRIEF))
            .flatMap(List::stream)
            .toList();

    assertThat(
            read.stream()
                .flatMap(document ->
                    BudgetedTexts.sentences(document).stream()
                        .filter(sentence -> sentence.split("\\s+").length > 25)
                        .map(sentence ->
                            document.getFileName()
                                + ": "
                                + sentence
                                + BudgetedTexts.prompt(document))))
        .isEmpty();
  }

  @Test
  void commentsAreAtMostFifteenPercentOfTheCode() {
    List<String> code =
        SourceRoots.javaFiles()
            .flatMap(source -> DocFiles.read(source).lines().toList().stream())
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .toList();
    long comments =
        code.stream()
            .filter(line -> line.startsWith("//") || line.startsWith("/*") || line.startsWith("*"))
            .count();

    assertThat(100 * comments / code.size())
        .describedAs("%d comment lines in %d", comments, code.size())
        .isLessThanOrEqualTo(15);
  }

  @Test
  void readsEveryFileItClaimsToCheck() {
    assertThat(markdownIn(Path.of("../docs/rules"))).hasSizeGreaterThan(3);
    assertThat(markdownIn(Path.of("../docs"))).hasSizeGreaterThan(3);
    assertThat(markdownIn(Path.of("../docs/decisions"))).hasSizeGreaterThan(1);
    assertThat(BudgetedTexts.SESSION_BRIEF).isRegularFile();
    assertThat(specs()).hasSizeGreaterThan(5);
    assertThat(SourceRoots.javaFiles().count()).isGreaterThan(100);
  }
}
