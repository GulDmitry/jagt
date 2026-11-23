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
import java.util.stream.Stream;

/** The contract each script of scripts.list keeps: a case file, a header, a tagged case per key. */
public final class GuardsGate {

  private static final String TESTS = "scripts/guards/test";
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
    if (!Files.isRegularFile(list())) {
      problems.add("no list: " + TESTS + "/scripts.list is missing");
      return;
    }
    Set<String> seen = new HashSet<>();
    for (Path script : listed()) {
      Path cases = caseFile(script.getFileName().toString());
      checkContract(script, cases, seen);
      if (lines(cases).stream().noneMatch(line -> line.startsWith("@test "))) {
        problems.add("no case: " + relative(script) + " has no case in " + relative(cases));
      }
    }
    checkGaps(seen);
  }

  private List<Path> listed() {
    List<Path> scripts = new ArrayList<>();
    for (String entry : lines(list())) {
      if (entry.isBlank()) {
        continue;
      }
      List<Path> matches = expand(entry.strip());
      if (matches.isEmpty()) {
        problems.add("missing script: " + entry + " names no file");
      }
      scripts.addAll(matches);
    }
    return scripts;
  }

  private List<Path> expand(String glob) {
    int star = glob.indexOf('*');
    String fixed = star < 0 ? glob : glob.substring(0, star);
    int slash = fixed.lastIndexOf('/');
    Path base = root.resolve(slash < 0 ? "" : fixed.substring(0, slash));
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + root.resolve(glob));
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

  private Path list() {
    return root.resolve(TESTS).resolve("scripts.list");
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
