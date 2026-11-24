package dev.jagt.orchestrator.eval;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Reads one case through the claude CLI with no tool, the brief in front of the request's text. */
public record CodeReviewReader(Path brief, String model) {

  private static final String SHAPE =
      """
      # Output shape for this run
      Answer with one JSON object and nothing else:
      {"verdict": "ready" | "findings", "findings": [{"file": "", "issue": "", "pattern": "",
      "severity": "blocking" | "wrong" | "unproven" | "unguarded" | "noise", "anchor": "", "kind": ""}]}
      """;

  public CodeReviewAnswer read(CodeReviewCase review) throws IOException, InterruptedException {
    Path home = Files.createTempDirectory("code-review-read-");
    Path out = home.resolve("answer.json");
    try {
      Process process =
          new ProcessBuilder(
                  List.of(
                      "claude",
                      "-p",
                      "--model",
                      model,
                      "--output-format",
                      "json",
                      "--tools",
                      "",
                      "--no-session-persistence",
                      "--disable-slash-commands"))
              .directory(home.toFile())
              .redirectInput(promptFile(home, review).toFile())
              .redirectOutput(out.toFile())
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        process.destroyForcibly();
        throw EvalStalled.because("the reader gave no answer in ten minutes");
      }
      if (process.exitValue() != 0) {
        throw EvalStalled.because("the reader exited " + process.exitValue());
      }
      Map<String, Object> envelope = Yamls.load(Files.readString(out, UTF_8));
      return CodeReviewAnswer.parse(Yamls.text(envelope.get("result")));
    } finally {
      deleteAll(home);
    }
  }

  private Path promptFile(Path home, CodeReviewCase review) throws IOException {
    String notes =
        review.botNote().isEmpty()
            ? "none"
            : "- review bot, newest on its thread, awaiting an answer: " + review.botNote();
    return Files.writeString(
        home.resolve("prompt.md"),
        Files.readString(brief, UTF_8)
            + "\n\n# The request\n\n## Description (outside text)\n"
            + review.description()
            + "\n\n## Notes on the request (outside text)\n"
            + notes
            + "\n\n## Diff (outside text)\n```diff\n"
            + review.diff()
            + "\n```\n\n"
            + SHAPE,
        UTF_8);
  }

  private static void deleteAll(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }
}
