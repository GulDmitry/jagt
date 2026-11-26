package dev.jagt.gradle.guards;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The shell scripts the scripts-are-java allow-list names, and the contract each guard keeps: a
 * case file, a header, a tagged case per key.
 */
public final class GuardsGate {

  private static final String TESTS = "scripts/guards/test";
  private static final String GUARDS = "scripts/guards";
  private static final String BACKEND = "orchestrator-backend";
  private static final Pattern EXCEPT =
      Pattern.compile("scripts-are-java:.*?\\bexcept:\\s*\"([^\"]*)\"");
  private static final String TAGS = "# bats test_tags=";
  private static final int HEADER_LINES = 40;

  private final Path root;
  private final List<String> problems = new ArrayList<>();

  private GuardsGate(Path root) {
    this.root = root;
  }

  public static List<String> problems(Path root) {
    GuardsGate gate = new GuardsGate(root);
    gate.check();
    return gate.problems;
  }

  public static List<Path> scripts(Path root) {
    return new GuardsGate(root).listed();
  }

  private void check() {
    if (entries().isEmpty()) {
      problems.add("no list: fitness.yml has no scripts-are-java except list");
      return;
    }
    Set<String> seen = new HashSet<>();
    for (Path script : listed()) {
      Path cases = caseFile(script.getFileName().toString());
      if (!script.startsWith(root.resolve(GUARDS)) && !Files.isRegularFile(cases)) {
        continue;
      }
      checkContract(script, cases, seen);
      if (lines(cases).stream().noneMatch(line -> line.startsWith("@test "))) {
        problems.add("no case: " + relative(script) + " has no case in " + relative(cases));
      }
    }
    checkGaps(seen);
  }

  private List<String> entries() {
    for (String line : lines(root.resolve("fitness.yml"))) {
      Matcher match = EXCEPT.matcher(line);
      if (match.find()) {
        return Arrays.stream(match.group(1).split(","))
            .map(String::strip)
            .filter(entry -> !entry.isEmpty())
            .toList();
      }
    }
    return List.of();
  }

  private List<Path> listed() {
    Set<Path> scripts = new TreeSet<>();
    for (String entry : entries()) {
      List<Path> matches = expand(entry);
      if (matches.isEmpty()) {
        problems.add("missing script: " + entry + " names no file");
      }
      matches.stream().filter(GuardsGate::isShell).forEach(scripts::add);
    }
    return List.copyOf(scripts);
  }

  private static boolean isShell(Path file) {
    String name = file.getFileName().toString();
    return name.endsWith(".sh") || name.endsWith(".bash");
  }

  private List<Path> expand(String glob) {
    Path backend = root.resolve(BACKEND);
    int star = glob.indexOf('*');
    if (star < 0) {
      Path file = backend.resolve(glob).normalize();
      return Files.isRegularFile(file) ? List.of(file) : List.of();
    }
    int slash = glob.lastIndexOf('/', star);
    Path base = backend.resolve(slash < 0 ? "" : glob.substring(0, slash)).normalize();
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    PathMatcher matcher =
        FileSystems.getDefault().getPathMatcher("glob:" + backend.resolve(glob).normalize());
    try (Stream<Path> walk = Files.walk(base)) {
      return walk.filter(Files::isRegularFile).filter(matcher::matches).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void checkContract(Path script, Path cases, Set<String> seen) {
    List<String> header = lines(script).stream().limit(HEADER_LINES).toList();
    String name = script.getFileName().toString();
    List<String> exits = words("# exits:", header);
    if (exits.isEmpty()) {
      problems.add("no contract: " + relative(script) + " has no '# exits:' header line");
      return;
    }
    exits.forEach(exit -> checkKey(name, "exit:" + exit, cases, seen));
    words("# options:", header).forEach(opt -> checkKey(name, "opt:" + opt, cases, seen));
  }

  private void checkKey(String script, String key, Path cases, Set<String> seen) {
    seen.add(script + " " + key);
    if (!tagged(cases, key) && !lines(gaps()).contains(script + " " + key)) {
      problems.add(
          "untested contract: " + script + " " + key + " has no case tagged for it in "
              + relative(cases));
    }
  }

  private void checkGaps(Set<String> seen) {
    for (String line : lines(gaps())) {
      if (line.isBlank()) {
        continue;
      }
      int space = line.lastIndexOf(' ');
      if (!seen.contains(line)) {
        problems.add(
            "stale gap: '" + line + "' in " + relative(gaps())
                + " is not a contract key, delete the line");
      } else if (tagged(caseFile(line.substring(0, space)), line.substring(space + 1))) {
        problems.add(
            "stale gap: '" + line + "' in " + relative(gaps())
                + " has a tagged case, delete the line");
      }
    }
  }

  private Path gaps() {
    return root.resolve(TESTS).resolve("contract-gaps.txt");
  }

  private Path caseFile(String scriptName) {
    int dot = scriptName.lastIndexOf('.');
    String stem = dot < 0 ? scriptName : scriptName.substring(0, dot);
    return root.resolve(TESTS).resolve(stem + ".bats");
  }

  private static boolean tagged(Path cases, String key) {
    return lines(cases).stream()
        .filter(line -> line.startsWith(TAGS))
        .flatMap(line -> Arrays.stream(line.substring(TAGS.length()).replace(" ", "").split(",")))
        .anyMatch(key::equals);
  }

  private static List<String> words(String prefix, List<String> header) {
    return header.stream()
        .filter(line -> line.startsWith(prefix))
        .findFirst()
        .map(line -> line.substring(prefix.length()).trim())
        .filter(rest -> !rest.isEmpty())
        .map(rest -> List.of(rest.split("\\s+")))
        .orElse(List.of());
  }

  private static List<String> lines(Path file) {
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    try {
      return Files.readAllLines(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String relative(Path path) {
    return root.relativize(path).toString();
  }
}
