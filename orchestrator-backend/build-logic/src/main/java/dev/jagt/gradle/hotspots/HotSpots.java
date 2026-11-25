package dev.jagt.gradle.hotspots;

import dev.jagt.gradle.exec.Git;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;

/** The main-source classes the fix commits of the last 30 days return to most. */
public final class HotSpots {

  private static final Duration WINDOW = Duration.ofDays(30);
  private static final int TOP = 10;
  private static final String COMMIT_MARK = "\u0001";
  private static final Pattern MAIN_SOURCE =
      Pattern.compile(".*/src/main/java/.*(?<!package-info|module-info)\\.java");

  /** One class, the fix commits that touched it and its current length. */
  public record Spot(String name, int fixes, int lines) {

    public String line() {
      return name + " " + fixes + " " + lines;
    }
  }

  private final Path root;
  private final Git git;
  private final Clock clock;

  public HotSpots(Path root, Git git, Clock clock) {
    this.root = root;
    this.git = git;
    this.clock = clock;
  }

  /** Fails when a class holds more fix commits than the ceiling, or the top count fell below it. */
  public static void enforce(List<Spot> top, int max) {
    int highest = top.isEmpty() ? 0 : top.getFirst().fixes();
    if (highest > max) {
      throw new GradleException(
          top.getFirst().name()
              + " has "
              + highest
              + " fix commits in 30 days, over hotSpots.max "
              + max
              + ": cut it as its own task or record why it stays");
    }
    if (highest < max) {
      throw new GradleException(
          "the top count is "
              + highest
              + ", below hotSpots.max "
              + max
              + ": lower hotSpots.max to "
              + highest);
    }
  }

  public List<Spot> top() {
    Instant since = clock.instant().minus(WINDOW);
    requireHistory(since);
    List<String> log =
        git.lines(
            "-c",
            "core.quotePath=false",
            "log",
            "--no-merges",
            "--since=" + since,
            "--name-only",
            "--format=" + COMMIT_MARK + "%(trailers:key=Defect-family,valueonly,separator=%x2C)");
    Map<String, Integer> fixes = new HashMap<>();
    boolean fix = false;
    for (String line : log) {
      if (line.startsWith(COMMIT_MARK)) {
        fix = isFix(line.substring(COMMIT_MARK.length()));
      } else if (fix
          && MAIN_SOURCE.matcher(line).matches()
          && Files.isRegularFile(root.resolve(line))) {
        fixes.merge(line, 1, Integer::sum);
      }
    }
    return fixes.entrySet().stream()
        .map(entry -> new Spot(simpleName(entry.getKey()), entry.getValue(), lines(entry.getKey())))
        .sorted(Comparator.comparingInt(Spot::fixes).reversed().thenComparing(Spot::name))
        .limit(TOP)
        .toList();
  }

  private void requireHistory(Instant since) {
    if (!git.lines("rev-parse", "--is-shallow-repository").contains("true")) {
      return;
    }
    String stamp = git.lines("log", "--format=%ct").getLast();
    Instant oldest = Instant.ofEpochSecond(Long.parseLong(stamp));
    if (oldest.isAfter(since)) {
      throw new GradleException(
          "the checkout is shallow: its history starts "
              + oldest
              + ", after "
              + since
              + "; fetch the last 30 days (git fetch --shallow-since)");
    }
  }

  private static boolean isFix(String families) {
    return Arrays.stream(families.split(","))
        .map(String::strip)
        .anyMatch(family -> !family.isEmpty() && !family.equals("L"));
  }

  private static String simpleName(String path) {
    String file = path.substring(path.lastIndexOf('/') + 1);
    return file.substring(0, file.length() - ".java".length());
  }

  private int lines(String path) {
    try {
      return (int) Files.readString(root.resolve(path)).lines().count();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
