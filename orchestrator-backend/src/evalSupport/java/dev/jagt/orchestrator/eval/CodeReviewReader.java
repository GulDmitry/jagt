package dev.jagt.orchestrator.eval;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    return CodeReviewAnswer.parse(new ToollessCli(model).answer(prompt(review)));
  }

  private String prompt(CodeReviewCase review) throws IOException {
    String notes =
        review.botNote().isEmpty()
            ? "none"
            : "- review bot, newest on its thread, awaiting an answer: " + review.botNote();
    return Files.readString(brief, UTF_8)
        + "\n\n# The request\n\n## Description (outside text)\n"
        + review.description()
        + "\n\n## Notes on the request (outside text)\n"
        + notes
        + "\n\n## Diff (outside text)\n```diff\n"
        + review.diff()
        + "\n```\n\n"
        + SHAPE;
  }
}
