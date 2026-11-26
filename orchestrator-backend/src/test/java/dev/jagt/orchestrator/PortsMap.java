package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClass;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PortsMap {

  private static final Pattern ROW = Pattern.compile("(?m)^\\| (`.+?) \\| (.+) \\|$");
  private static final Pattern CODE = Pattern.compile("`([^`]+)`");
  private static final Pattern BRACES = Pattern.compile("\\{([^{}]*)}");
  private static final String SECTION = "## Ports and who answers them";
  private static final String LEAD_ENDS = " — ";

  private PortsMap() {}

  public record Row(List<String> ports, List<String> leading, List<String> named) {}

  public static List<Row> rows() {
    String text = DocFiles.read(Path.of("../ARCHITECTURE.md"));
    String section = text.substring(text.indexOf(SECTION));
    String body = section.substring(0, section.indexOf("\n## ", 1));
    Matcher found = ROW.matcher(body);
    List<Row> rows = new ArrayList<>();
    while (found.find()) {
      String answer = found.group(2);
      int lead = answer.contains(LEAD_ENDS) ? answer.indexOf(LEAD_ENDS) : answer.length();
      rows.add(
          new Row(
              names(found.group(1), false),
              names(answer.substring(0, lead), true),
              names(answer, true)));
    }
    return rows;
  }

  public static List<JavaClass> classesNamed(String name) {
    String suffix = "." + name.replace('/', '.');
    return MainClasses.CODE.stream()
        .filter(type -> !type.isNestedClass())
        .filter(type -> type.getName().endsWith(suffix))
        .toList();
  }

  public static List<String> portsNotInMain() {
    return rows().stream()
        .flatMap(row -> row.ports().stream())
        .filter(port -> classesNamed(port).isEmpty())
        .toList();
  }

  public static List<String> answersNotInMain() {
    return rows().stream()
        .flatMap(row -> row.named().stream())
        .filter(answer -> classesNamed(answer).isEmpty())
        .toList();
  }

  public static List<String> answersNotImplementingTheirPort() {
    return rows().stream()
        .flatMap(row ->
            row.leading().stream()
                .filter(answer -> !implementsAny(answer, row.ports()))
                .map(answer -> answer + " for " + row.ports()))
        .toList();
  }

  private static boolean implementsAny(String answer, List<String> ports) {
    return classesNamed(answer).stream()
        .anyMatch(type ->
            ports.stream()
                .flatMap(port -> classesNamed(port).stream())
                .anyMatch(port -> type.isAssignableTo(port.getName())));
  }

  private static List<String> names(String cell, boolean paths) {
    return CODE.matcher(cell)
        .results()
        .map(match -> match.group(1))
        .flatMap(PortsMap::expanded)
        .filter(name -> !name.contains("<") && !name.endsWith("/"))
        .filter(name -> !paths || name.contains("/"))
        .collect(Collectors.toList());
  }

  private static Stream<String> expanded(String name) {
    Matcher brace = BRACES.matcher(name);
    if (!brace.find()) {
      return Stream.of(name);
    }
    return Stream.of(brace.group(1).split(","))
        .map(option -> name.substring(0, brace.start()) + option + name.substring(brace.end()))
        .flatMap(PortsMap::expanded);
  }
}
