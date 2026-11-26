package dev.jagt.orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SpecSymbols {

  private static final Pattern CODE = Pattern.compile("`([^`\\n]+)`");
  private static final Pattern SYMBOL =
      Pattern.compile(
          "(?:[a-z]+/)*([A-Z][a-z0-9]+(?:[A-Z][a-z0-9]*)+)"
              + "(?:(?:[.#]|::)([a-z]\\w*)(?:\\(\\))?)?");
  private static final Pattern TYPE =
      Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)");
  private static final Pattern JOB_ID =
      Pattern.compile("String id\\(\\)\\s*\\{\\s*return \"([^\"]+)\";");
  private static final Set<String> OTHER_PARTIES =
      Set.of("ServiceLoader", "TaskScheduler", "AskUserQuestion");
  private static final String JOB = SourceRoots.BASE_PACKAGE + ".job.Job";
  private static final String HOST_JOBS = SourceRoots.BASE_PACKAGE + ".job.host";

  private SpecSymbols() {}

  public static List<String> missingFromMain() {
    Map<String, String> main =
        typesIn(Stream.concat(SourceRoots.mainJavaFiles(), fitnessLibrarySources()));
    Map<String, String> tests = typesIn(SourceRoots.javaFiles());
    return DocFiles.specs().stream()
        .flatMap(spec -> symbolsIn(spec).filter(symbol -> !found(symbol, main, tests)))
        .toList();
  }

  public static List<String> hostJobIdsNamedNowhere() {
    String specs = DocFiles.specs().stream().map(DocFiles::read).collect(Collectors.joining("\n"));
    return hostJobIds().stream().filter(id -> !specs.contains("`" + id + "`")).toList();
  }

  public static List<String> hostJobIds() {
    Set<String> hosts =
        MainClasses.CODE.stream()
            .filter(type -> type.getPackageName().equals(HOST_JOBS))
            .filter(type -> type.isAssignableTo(JOB))
            .map(type -> type.getSimpleName() + ".java")
            .collect(Collectors.toSet());
    return SourceRoots.mainJavaFiles()
        .filter(file -> hosts.contains(file.getFileName().toString()))
        .flatMap(file -> JOB_ID.matcher(DocFiles.read(file)).results())
        .map(match -> match.group(1))
        .toList();
  }

  private static Stream<String> symbolsIn(Path spec) {
    return CODE.matcher(DocFiles.withoutCodeBlocks(spec))
        .results()
        .map(match -> match.group(1))
        .filter(token -> SYMBOL.matcher(token).matches());
  }

  private static boolean found(String token, Map<String, String> main, Map<String, String> all) {
    Matcher symbol = SYMBOL.matcher(token);
    symbol.matches();
    String type = symbol.group(1);
    String member = symbol.group(2);
    boolean fixture = type.endsWith("Test") || type.endsWith("Fitness");
    String source = (fixture ? all : main).get(type);
    if (source == null) {
      return OTHER_PARTIES.contains(type)
          || Files.exists(Path.of("../board/components/" + type + ".js"));
    }
    return member == null || Pattern.compile("\\b" + member + "\\b").matcher(source).find();
  }

  private static Stream<Path> fitnessLibrarySources() {
    return SourceRoots.javaFilesUnder(Path.of("fitness"))
        .filter(file -> file.toString().contains("/src/main/java/"));
  }

  private static Map<String, String> typesIn(Stream<Path> files) {
    Map<String, String> sources = new HashMap<>();
    files.forEach(file -> {
      String text = DocFiles.read(file);
      TYPE.matcher(text)
          .results()
          .forEach(match -> sources.merge(match.group(1), text, (a, b) -> a + b));
    });
    return sources;
  }
}
