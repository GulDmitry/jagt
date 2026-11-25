package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public final class HandedBackSession {

  public static final String RECORD = "eval-session.jsonl";

  private static final JsonMapper JSON = new JsonMapper();

  private HandedBackSession() {}

  /** A hand-back is refused without notes, and a task starts with its instructions relayed. */
  public static void files(Path worktree, String instructions) throws IOException {
    Files.writeString(worktree.resolve("task_context.md"), instructions);
    Files.writeString(worktree.resolve("task_notes.md"), "");
  }

  /** Always a file: the agent CLI removes an empty record directory, which reads as unreadable. */
  public static void record(Path recordDirectory, List<String> said) throws IOException {
    Files.createDirectories(recordDirectory);
    List<String> lines =
        said.stream()
            .map(text ->
                JSON.writeValueAsString(
                    Map.of(
                        "type",
                        "user",
                        "entrypoint",
                        "cli",
                        "origin",
                        Map.of("kind", "human"),
                        "message",
                        Map.of("content", text))))
            .toList();
    Files.write(recordDirectory.resolve(RECORD), lines);
  }
}
