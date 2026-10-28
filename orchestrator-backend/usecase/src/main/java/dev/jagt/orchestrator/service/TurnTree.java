package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Whether a turn changed the worktree, measured against the tree the previous turn ended on. */
@Service
@RequiredArgsConstructor
public class TurnTree {

    /** The tree state the last turn end saw. */
    public static final String ENDED_ON = "turn-end:tree";

    private final WorktreeChanges changes;
    private final TaskTurns turns;

    /** With no end seen yet, a tree holding uncommitted work counts as changed; a tree git cannot read never does. */
    public boolean changedUnreported(String taskId, TaskState task) {
        Optional<String> now = changes.state(task);
        if (now.isEmpty()) {
            return false;
        }
        String before = turns.of(taskId).mark(ENDED_ON);
        turns.mark(taskId, ENDED_ON, now.get());
        return before == null ? changes.anyUncommitted(task) : !before.equals(now.get());
    }
}
