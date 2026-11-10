package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DecisionIndexTest {

  @Test
  void indexesEveryRecordedDecision() throws Exception {
    String index = Files.readString(Path.of("../docs/decisions/README.md"));
    try (Stream<Path> files = Files.list(Path.of("../docs/decisions"))) {
      assertThat(
              files
                  .map(file -> file.getFileName().toString())
                  .filter(name -> name.matches("\\d{4}-.*\\.md")))
          .isNotEmpty()
          .allSatisfy(record -> assertThat(index).contains("(" + record + ")"));
    }
  }

  @Test
  void anAmendedOrSupersededRecordNamesItsAmenderInItsStatus() throws Exception {
    List<Path> records;
    try (Stream<Path> files = Files.list(Path.of("../docs/decisions"))) {
      records =
          files.filter(file -> file.getFileName().toString().matches("\\d{4}-.*\\.md")).toList();
    }

    assertThat(records.stream().flatMap(record -> unnamedAmenders(record, records))).isEmpty();
  }

  private static Stream<String> unnamedAmenders(Path record, List<Path> records) {
    String number = record.getFileName().toString().substring(0, 4);
    Matcher text = Pattern.compile("(?s)^(.*?)\\n## Context").matcher(read(record));
    String own = text.find() ? text.group(1).replaceAll("\\[(\\d{4})]\\([^)]*\\)", "$1") : "";
    return Pattern.compile("(?i)\\b(?:amends|supersedes)\\b[^.:;·]*")
        .matcher(own)
        .results()
        .flatMap(claim -> Pattern.compile("\\b0\\d{3}\\b").matcher(claim.group()).results())
        .map(MatchResult::group)
        .filter(target -> !namesAmender(records, target, number))
        .map(target -> target + " does not name " + number);
  }

  private static boolean namesAmender(List<Path> records, String target, String number) {
    Path other =
        records.stream()
            .filter(file -> file.getFileName().toString().startsWith(target + "-"))
            .findFirst()
            .orElseThrow();
    Matcher back = Pattern.compile("(?s)## Status\\s*(.*?)\\n## ").matcher(read(other));
    return back.find() && back.group(1).contains(number);
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
