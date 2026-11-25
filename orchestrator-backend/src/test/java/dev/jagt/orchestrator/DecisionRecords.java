package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

  static Stream<String> exceptionsWithoutQuorum(List<Path> records, String fitnessYml) {
    Set<String> named =
        Pattern.compile("\\bexception: \"(\\d{4})\"")
            .matcher(fitnessYml)
            .results()
            .map(row -> row.group(1))
            .collect(Collectors.toSet());
    Stream<String> unknown =
        named.stream()
            .filter(number -> records.stream().noneMatch(record -> numberOf(record).equals(number)))
            .map(number -> number + " is named by an exception row and is no decision");
    Stream<String> unquorumed =
        records.stream()
            .filter(record -> named.contains(numberOf(record)) || grantsException(statusOf(record)))
            .flatMap(record -> missingQuorum(numberOf(record), statusOf(record)));
    return Stream.concat(unknown, unquorumed);
  }

  private static boolean grantsException(String status) {
    return Pattern.compile("(?m)^Exception\\b").matcher(status).find();
  }

  private static Stream<String> missingQuorum(String number, String status) {
    boolean owner =
        Pattern.compile("(?m)^Owner approved \\d{4}-\\d{2}-\\d{2}\\b").matcher(status).find();
    boolean reviewer =
        Pattern.compile("(?m)^Reviewer agreed \\d{4}-\\d{2}-\\d{2}, \\S").matcher(status).find();
    return Stream.of(
            owner ? null : number + " grants an exception without the owner's approval",
            reviewer ? null : number + " grants an exception without a reviewer's agreement")
        .filter(Objects::nonNull);
  }

  private static String numberOf(Path record) {
    return record.getFileName().toString().substring(0, 4);
  }

  private static String statusOf(Path record) {
    Matcher status = Pattern.compile("(?s)## Status\\s*(.*?)\\n## ").matcher(DocFiles.read(record));
    return status.find() ? status.group(1) : "";
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
