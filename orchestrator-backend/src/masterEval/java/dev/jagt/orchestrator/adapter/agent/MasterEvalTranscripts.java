package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.eval.HandedBackSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class MasterEvalTranscripts {

  private MasterEvalTranscripts() {}

  public static void nothingTypedIn(Path worktree) throws IOException {
    typedIn(worktree, List.of());
  }

  public static void typedIn(Path worktree, List<String> said) throws IOException {
    HandedBackSession.record(
        ClaudeTranscripts.projectsDir().resolve(ClaudeTranscripts.slug(worktree)), said);
  }
}
