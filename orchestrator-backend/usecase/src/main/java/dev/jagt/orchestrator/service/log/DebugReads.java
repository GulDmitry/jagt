package dev.jagt.orchestrator.service.log;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reads of jagt's own records and a session's words; nothing here writes. */
@Service
@RequiredArgsConstructor
public class DebugReads {

  private final OrchestratorPaths paths;
  private final StateService state;
  private final AgentRuntime runtime;

  public String stops(int lines) {
    return tail(paths.stateFile().resolveSibling("stops.jsonl"), lines);
  }

  public String stopsReviewed(int lines) {
    return tail(paths.stateFile().resolveSibling("stops-reviewed.jsonl"), lines);
  }

  /** Empty where the file does not exist yet. */
  public String tail(Path file, int lines) {
    if (!Files.isRegularFile(file)) {
      return "";
    }
    try {
      List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
      return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file.getFileName(), e);
    }
  }

  public String transcript(String taskId, int maxChars) {
    String id = state.canonicalTaskId(taskId);
    TaskState task =
        state.task(id).orElseThrow(() -> new IllegalArgumentException("No task '" + taskId + "'"));
    String worktree = task.worktreePath();
    if (worktree == null) {
      return "";
    }
    return runtime.conversationTail(Path.of(worktree), maxChars, Set.of()).orElse("");
  }
}
