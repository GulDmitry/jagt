package dev.jagt.orchestrator.service.repo;

import dev.jagt.orchestrator.port.codehost.RequestlessShip;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read when asked, from the configuration and the worktrees: nothing is recorded that a later round
 * or a config change could outlive.
 */
@Service
@RequiredArgsConstructor
public class HostlessShips implements RequestlessShip {

  private final ConfigService configService;
  private final WorktreeChanges changes;

  @Override
  public boolean stands(TaskState task) {
    if (task.standing().status() != TaskStatus.REVIEW_PENDING || task.repos().isEmpty()) {
      return false;
    }
    ConfigFile config = configService.load();
    return task.repos().stream()
            .allMatch(repo -> config.hasNoCodeHost(Objects.requireNonNull(repo.project())))
        && !changes.anythingNewToShip(task);
  }
}
