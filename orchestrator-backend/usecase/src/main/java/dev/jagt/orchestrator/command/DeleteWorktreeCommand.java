package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.WorktreeOrphans;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteWorktreeCommand implements GlobalCommand {

  private final WorktreeOrphans orphans;

  @Override
  public String id() {
    return "delete-worktree";
  }

  @Override
  public int rank() {
    return 166;
  }

  @Override
  public String hint() {
    return "delete one orphaned worktree directory, with whatever it still holds";
  }

  @Override
  public List<String> usage() {
    return List.of("delete-worktree <name>");
  }

  @Override
  public Optional<String> whyNotTheMaster() {
    return Optional.of(
        "an orphan may hold work nobody committed; only a human decides it is rubbish");
  }

  @Override
  public String run(String tail) {
    String name = tail.strip();
    if (name.isEmpty()) {
      throw new IllegalArgumentException(
          "usage: delete-worktree <name> — `worktrees` lists them");
    }
    return orphans.delete(name);
  }
}
