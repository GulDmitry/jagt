package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AcceptanceWorkspaceFiles {

  public static final List<String> FLOWS_COMMAND = List.of("node", "runflow.mjs");

  private static final String RUNS = "runs.log";
  private static final String REPORT = "acceptance-report.md";
  private static final String RUNNER =
      """
      import { spawnSync } from 'node:child_process';
      import { appendFileSync } from 'node:fs';
      const run = spawnSync('node', ['--test', ...process.argv.slice(2)], { stdio: 'inherit', env: process.env });
      appendFileSync('runs.log', process.env.BASE_URL + ' ' + process.argv.slice(2).join(' ') + ' exit=' + run.status + '\\n');
      process.exit(run.status ?? 1);
      """;

  private AcceptanceWorkspaceFiles() {}

  public static void seed(Path directory, AcceptanceCase expected) {
    try {
      Files.createDirectories(directory);
      Files.writeString(directory.resolve("runflow.mjs"), RUNNER);
      for (var file : expected.workspace().files().entrySet()) {
        Path target = directory.resolve(file.getKey());
        Files.createDirectories(target.getParent());
        Files.writeString(target, file.getValue());
      }
    } catch (IOException unwritten) {
      throw new UncheckedIOException(unwritten);
    }
  }

  public static Set<String> snapshot(Path directory) {
    try (Stream<Path> walk = Files.walk(directory)) {
      return walk.filter(Files::isRegularFile)
          .map(directory::relativize)
          .filter(relative -> !hidden(relative))
          .map(Path::toString)
          .collect(Collectors.toSet());
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static boolean hidden(Path relative) {
    return java.util.stream.StreamSupport.stream(relative.spliterator(), false)
        .anyMatch(part -> part.toString().startsWith("."));
  }

  /** Blank when what the run left in {@code watched} is what the case expects of it. */
  public static String problem(
      AcceptanceCase expected, Set<String> before, Path watched, String environmentUrl) {
    Set<String> added = new HashSet<>(snapshot(watched));
    added.removeAll(before);
    added.remove(RUNS);
    added.remove(REPORT);
    String writes = expected.workspace().writes();
    if ("none".equals(writes) && !added.isEmpty()) {
      return "wrote " + added + " where nothing new was expected";
    }
    if ("new".equals(writes) && added.stream().noneMatch(name -> name.startsWith("flows/"))) {
      return "wrote no flow under flows/; new files: " + added;
    }
    return expected.workspace().held() && !ranBare(watched, environmentUrl)
        ? "never ran BASE_URL=" + environmentUrl + " " + String.join(" ", FLOWS_COMMAND)
        : "";
  }

  private static boolean ranBare(Path watched, String environmentUrl) {
    Path log = watched.resolve(RUNS);
    try {
      return Files.exists(log)
          && Files.readAllLines(log).stream().anyMatch(line -> line.startsWith(environmentUrl + " "));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
