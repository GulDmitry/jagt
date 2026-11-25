package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.AgentWorktree;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public final class FakeAgentRuntime implements AgentRuntime {

  private final Path sessionLog;

  public FakeAgentRuntime(Path sessionLog) {
    this.sessionLog = sessionLog;
  }

  @Override
  public String displayName() {
    return "fake agent";
  }

  @Override
  public String launchCommand(Path worktree, LaunchMode mode) {
    return "fake-agent";
  }

  @Override
  public Path systemKnowledgeFile(Path worktree) {
    return worktree.resolve(SYSTEM_KNOWLEDGE_FILE);
  }

  @Override
  public void provisionWorktree(AgentWorktree worktree) {}

  @Override
  public OptionalLong lastSessionActivity(Path worktree) {
    return OptionalLong.empty();
  }

  @Override
  public Optional<Path> sessionLogOf(Path worktree) {
    return Optional.of(sessionLog);
  }

  @Override
  public Optional<List<String>> humanSaid(Path worktree, Set<String> typedByJagt) {
    return Optional.of(List.of());
  }

  @Override
  public boolean readsWhatTheHumanTyped() {
    return true;
  }
}
