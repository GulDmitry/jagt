package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class BudgetedTexts {

  static final Path SESSION_BRIEF = Path.of("../roles/session/brief.md.dist");
  static final String PROMPT_RULE =
      " — a prompt: compress it, then run promptEval and roleEval -Prole=master"
          + " (docs/rules/testing.md)";

  private static final Pattern NOT_PROSE = Pattern.compile("^(```|\\||#|<|>)");
  private static final Pattern LIST_ITEM = Pattern.compile("^([-*]|\\d+\\.)\\s+");

  private BudgetedTexts() {}

  static String prompt(Path document) {
    return document.equals(SESSION_BRIEF) ? PROMPT_RULE : "";
  }

  static List<Path> pages() {
    return Stream.of(
            List.of(
                Path.of("../AGENTS.md"),
                Path.of("../README.md"),
                Path.of("../docs/decisions/README.md")),
            DocFiles.markdownIn(Path.of("../docs")),
            DocFiles.markdownIn(Path.of("../docs/features")),
            DocFiles.specs())
        .flatMap(List::stream)
        .toList();
  }

  static List<String> sentences(Path document) {
    List<String> blocks = new ArrayList<>();
    StringBuilder block = new StringBuilder();
    boolean fenced = false;
    for (String line : DocFiles.read(document).lines().toList()) {
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
        .map(text ->
            text.replaceAll("`[^`]*`", "CODE").replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1"))
        .flatMap(text -> Arrays.stream(text.split("(?<=[.!?;:])\\s+| · ")))
        .map(String::strip)
        .filter(sentence -> !sentence.isEmpty())
        .toList();
  }
}
