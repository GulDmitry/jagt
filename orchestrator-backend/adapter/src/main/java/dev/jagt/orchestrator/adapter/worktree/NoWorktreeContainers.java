package dev.jagt.orchestrator.adapter.worktree;

import dev.jagt.orchestrator.port.worktree.WorktreeContainers;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "containers", havingValue = "none")
public class NoWorktreeContainers implements WorktreeContainers {

  @Override
  public void stop(Path worktree) {}
}
