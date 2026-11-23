package dev.jagt.gradle.testsfor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TestsFor {

  public record Plan(List<String> lines, List<String> dropped) {}

  private static final List<String> OTHER_SETS =
      List.of(
          "LinuxTest",
          "InstallTest",
          "BoardTest",
          "E2e",
          "EvalSupport",
          "PromptEval",
          "MasterEval",
          "TriageEval",
          "AcceptanceEval");
  private static final String FITNESS_TEST = "root:FitnessTest";
  private static final String PITEST = " -x pitestDiff";

  private final Repo repo;
  private final Layout layout;

  TestsFor(Repo repo, Layout layout) {
    this.repo = repo;
    this.layout = layout;
  }

  public static TestsFor in(Path directory) {
    try {
      Repo repo = Repo.around(directory);
      Path inside = repo.top().toRealPath().relativize(directory.toRealPath());
      return new TestsFor(repo, new Layout(inside.toString().replace('\\', '/')));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public List<String> changedAgainst(String base) {
    return repo.changed(base);
  }

  public String stripRoot(String path) {
    String prefix = repo.top() + "/";
    return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
  }

  public Plan plan(List<String> paths) {
    Selection selection = new Selection();
    Classifier classifier = new Classifier(repo, layout, selection);
    paths.forEach(classifier::take);
    List<String> lines = new ArrayList<>();
    String compile = compile(selection);
    String check = check(selection);
    addGradle(lines, 600, compile);
    addGradle(lines, 600, check);
    if (!selection.tests.isEmpty()) {
      addGradle(lines, 900, testsLine(new ArrayList<>(selection.tests)) + PITEST);
    }
    List<String> dropped = new ArrayList<>();
    List<String> ruleEntries = ruleEntries(selection, dropped);
    if (!ruleEntries.isEmpty()) {
      addGradle(lines, 900, testsLine(ruleEntries) + PITEST);
    }
    if (selection.fitness) {
      lines.add("cd " + layout.directory() + "/fitness && timeout 600 ../gradlew test");
    }
    if (selection.buildLogic) {
      lines.add("cd " + layout.directory() + "/build-logic && timeout 600 ../gradlew test");
    }
    if (selection.board) {
      lines.add("cd board && timeout 600 sh -c 'npm run lint && npm test'");
    }
    return new Plan(lines, dropped);
  }

  private void addGradle(List<String> lines, int seconds, String tasks) {
    if (!tasks.isBlank()) {
      lines.add(
          "cd "
              + layout.directory()
              + " && timeout "
              + seconds
              + " ./gradlew "
              + tasks.stripLeading());
    }
  }

  private String compile(Selection selection) {
    List<String> tasks = new ArrayList<>();
    for (String module : selection.projects) {
      tasks.add(layout.prefix(module) + ":compileJava");
      tasks.add(layout.prefix(module) + ":compileTestJava");
    }
    for (String set : selection.otherSets) {
      tasks.add(layout.prefix(module(set)) + ":compile" + capitalised(set) + "Java");
    }
    if (selection.removedMain) {
      for (String module : Layout.EVERY_UNIT) {
        tasks.add(layout.prefix(module) + ":compileJava");
        tasks.add(layout.prefix(module) + ":compileTestJava");
      }
      OTHER_SETS.forEach(set -> tasks.add(":compile" + set + "Java"));
      tasks.add(":stand:compileStandJava");
    }
    return String.join(" ", new LinkedHashSet<>(tasks));
  }

  private String check(Selection selection) {
    List<String> tasks = new ArrayList<>();
    for (String module : selection.projects) {
      tasks.add(layout.prefix(module) + ":checkstyleMain");
      tasks.add(layout.prefix(module) + ":checkstyleTest");
    }
    for (String set : selection.otherSets) {
      tasks.add(layout.prefix(module(set)) + ":checkstyle" + capitalised(set));
    }
    return String.join(" ", new LinkedHashSet<>(tasks));
  }

  private static String module(String moduleAndSet) {
    return moduleAndSet.substring(0, moduleAndSet.indexOf(':'));
  }

  private static String capitalised(String moduleAndSet) {
    String set = moduleAndSet.substring(moduleAndSet.indexOf(':') + 1);
    return set.substring(0, 1).toUpperCase(Locale.ROOT) + set.substring(1);
  }

  private String testsLine(List<String> entries) {
    return Stream.concat(Stream.of(Layout.ROOT), Layout.MODULES.stream())
        .map(module -> testsOf(module, entries))
        .filter(part -> !part.isEmpty())
        .collect(Collectors.joining(" "));
  }

  private String testsOf(String module, List<String> entries) {
    String names =
        entries.stream()
            .filter(entry -> entry.startsWith(module + ":"))
            .map(entry -> " --tests '*" + entry.substring(module.length() + 1) + "'")
            .collect(Collectors.joining());
    return names.isEmpty() ? "" : layout.prefix(module) + ":test" + names;
  }

  private List<String> ruleEntries(Selection selection, List<String> dropped) {
    List<String> entries = new ArrayList<>();
    String tests = layout.testDirectory(Layout.ROOT);
    List<String> tracked = repo.trackedFiles(tests);
    for (String rule : selection.rules) {
      if (selection.tests.contains("root:" + rule + "Test")) {
        continue;
      }
      Pattern named = Pattern.compile("^" + Pattern.quote(tests) + "/.+/" + rule + "\\.java$");
      if (tracked.stream().anyMatch(file -> named.matcher(file).matches())) {
        if (!entries.contains(FITNESS_TEST)) {
          entries.add(FITNESS_TEST);
        }
      } else if (repo.tracked(tests + "/dev/jagt/orchestrator/" + rule + "Test.java")) {
        entries.add("root:" + rule + "Test");
      } else {
        dropped.add("tests-for: no rule test " + rule + "Test, dropped");
      }
    }
    if (selection.board) {
      entries.add("surface:BoardAssetsTest");
      entries.add(FITNESS_TEST);
    }
    return entries;
  }
}
