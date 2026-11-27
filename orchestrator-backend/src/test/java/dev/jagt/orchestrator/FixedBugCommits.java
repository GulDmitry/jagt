package dev.jagt.orchestrator;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FixedBugCommits {

  static final String CUTOFF = "865adc57dd11f34daaa5321682dfc55fced42a18";

  static final String POSTMORTEM_CUTOFF = "6969ccdd07149e212560f8c6e741721251380db1";

  static final String INVESTIGATION_CUTOFF = "8e101a5b98d17b612db1b7bc66d479aa91fb98e2";

  private static final List<String> POSTMORTEM_TRAILERS = List.of("Written", "Uncaught", "Unseen");
  private static final List<String> INVESTIGATION_TRAILERS = List.of("Kind", "Holds", "Prevents");
  private static final String RECORD = "\u001e";
  private static final String FIELD = "\u001f";
  private static final Pattern DEFECT_FAMILY = Pattern.compile("(?m)^Defect-family: [A-M]$");
  private static final Pattern CASE =
      Pattern.compile("(?m)^Case: (new \\S.*|existing \\S.*|none)$");
  private static final Pattern CASE_EXISTING = Pattern.compile("(?m)^Case: existing \\S.*$");
  private static final Pattern MAIN_CODE = Pattern.compile("(?:^|/)src/main/");

  private FixedBugCommits() {}

  record Commit(String sha, String message, List<String> files) {}

  public static List<String> defects() {
    return Stream.concat(
            commitsAfter(CUTOFF).stream().map(FixedBugCommits::defect),
            Stream.concat(
                commitsAfter(POSTMORTEM_CUTOFF).stream()
                    .map(commit -> missing(commit, POSTMORTEM_TRAILERS)),
                commitsAfter(INVESTIGATION_CUTOFF).stream()
                    .map(commit -> missing(commit, INVESTIGATION_TRAILERS))))
        .filter(defect -> !defect.isEmpty())
        .toList();
  }

  static String postmortem(Commit commit) {
    return missing(commit, POSTMORTEM_TRAILERS);
  }

  static String investigation(Commit commit) {
    return missing(commit, INVESTIGATION_TRAILERS);
  }

  private static String missing(Commit commit, List<String> trailers) {
    if (!DEFECT_FAMILY.matcher(commit.message()).find()) {
      return "";
    }
    return trailers.stream()
        .filter(trailer ->
            !Pattern.compile("(?m)^" + trailer + ": \\S.*$").matcher(commit.message()).find())
        .map(trailer ->
            commit.sha().substring(0, 9) + " carries Defect-family and no " + trailer + " trailer")
        .collect(Collectors.joining("; "));
  }

  static String defect(Commit commit) {
    if (!DEFECT_FAMILY.matcher(commit.message()).find()) {
      return "";
    }
    String sha = commit.sha().substring(0, 9);
    if (!CASE.matcher(commit.message()).find()) {
      return sha + " carries Defect-family and no Case trailer";
    }
    boolean touchesMain = commit.files().stream().anyMatch(file -> MAIN_CODE.matcher(file).find());
    boolean touchesSpec = commit.files().stream().anyMatch(file -> file.startsWith("openspec/"));
    if (touchesMain && !touchesSpec && !CASE_EXISTING.matcher(commit.message()).find()) {
      return sha + " fixes main code with no openspec file and no Case: existing";
    }
    return "";
  }

  static List<Commit> parse(String log) {
    return Arrays.stream(log.split(RECORD))
        .filter(record -> !record.isBlank())
        .map(record -> record.split(FIELD, 3))
        .map(parts ->
            new Commit(
                parts[0].strip(),
                parts[1],
                parts[2].lines().map(String::strip).filter(line -> !line.isEmpty()).toList()))
        .toList();
  }

  private static List<Commit> commitsAfter(String cutoff) {
    try {
      Process log =
          new ProcessBuilder(
                  "git",
                  "-C",
                  "..",
                  "log",
                  cutoff + "..HEAD",
                  "--name-only",
                  "--format=" + RECORD + "%H" + FIELD + "%B" + FIELD)
              .redirectError(Redirect.DISCARD)
              .start();
      String output = new String(log.getInputStream().readAllBytes(), UTF_8);
      return waitFor(log) == 0 ? parse(output) : List.of();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
