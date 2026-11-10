package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.WorktreeOrphans;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorktreesCommand implements GlobalCommand {

  private final WorktreeOrphans orphans;

  @Override
  public String id() {
    return "worktrees";
  }

  @Override
  public int rank() {
    return 165;
  }

  @Override
  public String hint() {
    return "worktree directories no task owns, what each holds, and the verb that deletes it";
  }

  @Override
  public boolean report() {
    return true;
  }

  @Override
  public String run(String tail) {
    return render(orphans.scan());
  }

  static String render(List<WorktreeOrphans.Orphan> orphans) {
    if (orphans.isEmpty()) {
      return "no orphaned worktree: every directory jagt cut belongs to a task.";
    }
    List<String> lines = new ArrayList<>();
    lines.add("orphaned worktrees (each may hold uncommitted work):");
    for (WorktreeOrphans.Orphan orphan : orphans) {
      lines.add(
          "  %-24s %-12s %2d copied secret file(s)   delete-worktree %s"
              .formatted(
                  orphan.path().getFileName(),
                  orphan.projectKey(),
                  orphan.secretFiles(),
                  orphan.path().getFileName()));
    }
    return String.join("\n", lines);
  }
}
