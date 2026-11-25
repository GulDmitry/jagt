package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.service.review.ReviewDrafts;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
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
    return changes.state(task).map(tree -> with(task, tree));
  }

  /** The content for a tree the caller has read already. */
  String with(TaskState task, String tree) {
    return tree + drafts.unspentDigest(task).map(d -> "|drafts:" + d).orElse("");
  }
}
