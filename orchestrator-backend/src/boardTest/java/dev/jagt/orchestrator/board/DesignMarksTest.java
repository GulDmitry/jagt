package dev.jagt.orchestrator.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DesignMarksTest {

  private static final Path BOARD = Path.of("../board");
  private static final Path RULES = Path.of("../docs/rules");
  private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
  private static final Pattern MARKING_DECLARATION =
      Pattern.compile(
          "(?:color|background[\\w-]*|border[\\w-]*|outline|text-decoration[\\w-]*)\\s*:[^;]*"
              + "(?:var\\(--(?:you|agent|ci|ok|danger|accent|you-bg)\\)|dashed|dotted)"
              + "|content\\s*:");
  private static final Pattern CLASS = Pattern.compile("\\.([A-Za-z][\\w-]*)");
  private static final Pattern ATTRIBUTE = Pattern.compile("\\[([\\w-]+)");
  private static final Pattern DESIGN_SELECTOR =
      Pattern.compile(
          "`((?:(?:button|a|td|body|article|table|span|div)?\\.[\\w-]+)+(?: [.\\w-]+)*)`");

  @Test
  void everyMarkClassHasOneDesignLineAndOneLegendSample() throws IOException {
    String css = Files.readString(BOARD.resolve("app.css")).replaceAll("(?s)/\\*.*?\\*/", "");
    String design =
        Files.readString(RULES.resolve("design.md"))
            + Files.readString(RULES.resolve("design-elements.md"));
    String legend;
    try (Stream<Path> files = Files.list(BOARD.resolve("ui/legend"))) {
      legend =
          files
              .map(file -> {
                try {
                  return Files.readString(file);
                } catch (IOException e) {
                  throw new java.io.UncheckedIOException(e);
                }
              })
              .reduce("", String::concat);
    }
    List<String> legendWords =
        Pattern.compile("([\"`])([^\"`]*)\\1")
            .matcher(legend)
            .results()
            .flatMap(literal -> Arrays.stream(literal.group(2).split("[^\\w-]+")))
            .toList();
    List<String> markFinalCompounds =
        RULE.matcher(css)
            .results()
            .filter(rule -> MARKING_DECLARATION.matcher(rule.group(2)).find())
            .flatMap(rule -> Arrays.stream(rule.group(1).split(",")))
            .map(String::trim)
            .filter(selector -> !selector.startsWith("@"))
            .map(selector -> selector.split("[\\s>+~]+"))
            .map(parts -> parts[parts.length - 1])
            .filter(compound -> !compound.matches(".*:(hover|focus[\\w-]*)"))
            .toList();

    List<String> classesWithoutDesignLine =
        markFinalCompounds.stream()
            .flatMap(compound -> CLASS.matcher(compound).results().map(m -> m.group(1)))
            .filter(name -> !name.startsWith("legend-"))
            .distinct()
            .filter(name -> !Pattern.compile("\\." + name + "(?![\\w-])").matcher(design).find())
            .toList();
    List<String> classesWithoutLegendSample =
        markFinalCompounds.stream()
            .flatMap(compound -> CLASS.matcher(compound).results().map(m -> m.group(1)))
            .filter(name -> !name.startsWith("legend-"))
            .distinct()
            .filter(name -> !legendWords.contains(name))
            .toList();
    List<String> attributesWithoutLegendSample =
        markFinalCompounds.stream()
            .flatMap(compound -> ATTRIBUTE.matcher(compound).results().map(m -> m.group(1)))
            .distinct()
            .filter(name ->
                !legend.contains(name)
                    && !legend.contains("dataset." + name.replaceFirst("^data-", "")))
            .toList();

    List<String> bodyStatesWithoutLegendSample =
        Pattern.compile("body\\.([\\w-]+)")
            .matcher(design)
            .results()
            .map(m -> m.group(1))
            .filter(name -> !legendWords.contains(name))
            .toList();

    assertThat(bodyStatesWithoutLegendSample).as("body states with no legend sample").isEmpty();
    assertThat(classesWithoutDesignLine).as("classes with no design.md line").isEmpty();
    assertThat(classesWithoutLegendSample).as("classes with no legend sample").isEmpty();
    assertThat(attributesWithoutLegendSample).as("attributes with no legend sample").isEmpty();
  }

  @Test
  void everySelectorDesignNamesExistsInTheStylesheet() throws IOException {
    String css = Files.readString(BOARD.resolve("app.css")).replaceAll("(?s)/\\*.*?\\*/", "");
    String design =
        Files.readString(RULES.resolve("design.md"))
            + Files.readString(RULES.resolve("design-elements.md"));
    List<String> cssSelectors =
        RULE.matcher(css)
            .results()
            .flatMap(rule -> Arrays.stream(rule.group(1).split(",")))
            .map(String::trim)
            .toList();
    Matcher named = DESIGN_SELECTOR.matcher(design);

    List<String> missing =
        named
            .results()
            .map(m -> m.group(1))
            .flatMap(token -> Arrays.stream(token.split(" ")))
            .distinct()
            .filter(compound ->
                cssSelectors.stream()
                    .noneMatch(selector ->
                        CLASS
                            .matcher(compound)
                            .results()
                            .allMatch(m ->
                                Pattern.compile("\\." + m.group(1) + "(?![\\w-])")
                                    .matcher(selector)
                                    .find())))
            .toList();

    assertThat(missing).as("selectors design.md names that app.css lacks").isEmpty();
  }
}
