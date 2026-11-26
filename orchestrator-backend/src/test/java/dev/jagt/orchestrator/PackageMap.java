package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PackageMap {

  private static final String ROOT = SourceRoots.BASE_PATH;
  private static final Pattern ROW = Pattern.compile("(?m)^\\| `([^`]+)` \\|");
  private static final Pattern REASON_ROW = Pattern.compile("(?m)^\\| `([^`]+)` \\| (.+) \\|$");
  private static final String OVER_TEN = "### Over ten classes";
  private static final String SINGLE = "### A single class";

  private PackageMap() {}

  public static Map<String, Long> onDisk() {
    return SourceRoots.mainJavaFiles()
        .filter(file -> !file.getFileName().toString().equals("package-info.java"))
        .filter(file -> file.toString().contains(ROOT))
        .collect(Collectors.groupingBy(PackageMap::packageOf, Collectors.counting()));
  }

  static Set<String> listed() {
    return rows(section(0));
  }

  public static Set<String> overTen() {
    return rows(section(1));
  }

  public static Map<String, String> overTenReasons() {
    Matcher row = REASON_ROW.matcher(section(1));
    return row.results()
        .collect(Collectors.toMap(match -> match.group(1), match -> match.group(2)));
  }

  static Set<String> single() {
    return rows(section(2));
  }

  private static String packageOf(Path file) {
    String path = file.toString();
    String inside = path.substring(path.indexOf(ROOT) + ROOT.length(), path.lastIndexOf('/'));
    return inside.isEmpty() ? "(root)" : inside.substring(1);
  }

  private static String section(int index) {
    String text = DocFiles.read(Path.of("../ARCHITECTURE.md"));
    String map = text.substring(text.indexOf("## The package map"));
    String body = map.substring(0, map.indexOf("\n## ", 1));
    String[] parts = body.split("(?m)^(?=### )", -1);
    return Stream.of(parts)
        .filter(part -> index == 0 ? !part.startsWith("### ") : part.startsWith(heading(index)))
        .collect(Collectors.joining());
  }

  private static String heading(int index) {
    return index == 1 ? OVER_TEN : SINGLE;
  }

  private static Set<String> rows(String text) {
    Matcher row = ROW.matcher(text);
    return Arrays.stream(row.results().map(match -> match.group(1)).toArray(String[]::new))
        .collect(Collectors.toSet());
  }
}
