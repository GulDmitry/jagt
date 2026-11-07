package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ReviewDrafts;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What a ship delivers: the tree state plus the unspent drafts; none where git could not read the
 * tree.
 */
@Component
@RequiredArgsConstructor
class ShipContent {

  private final WorktreeChanges changes;
  private final ReviewDrafts drafts;

  Optional<String> of(TaskState task) {
    return changes
        .state(task)
        .map(tree -> tree + drafts.unspentDigest(task).map(d -> "|drafts:" + d).orElse(""));
  }
}
