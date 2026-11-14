package dev.jagt.orchestrator.command.workspace;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.service.git.WorktreeOrphans;
import java.util.List;
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
  public String run(String tail) {
    String name = tail.strip();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("usage: delete-worktree <name> — `worktrees` lists them");
    }
    return orphans.delete(name);
  }
}
