package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class DecisionRecords {

  private static final Path DIRECTORY = Path.of("../docs/decisions");

  private DecisionRecords() {}

  static List<Path> records() {
    return DocFiles.markdownIn(DIRECTORY).stream()
        .filter(file -> file.getFileName().toString().matches("\\d{4}-.*\\.md"))
        .toList();
  }

  static Stream<String> unnamedAmenders(Path record, List<Path> records) {
    String number = record.getFileName().toString().substring(0, 4);
    Matcher text = Pattern.compile("(?s)^(.*?)\\n## Context").matcher(DocFiles.read(record));
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
    Matcher back = Pattern.compile("(?s)## Status\\s*(.*?)\\n## ").matcher(DocFiles.read(other));
    return back.find() && back.group(1).contains(number);
  }
}
