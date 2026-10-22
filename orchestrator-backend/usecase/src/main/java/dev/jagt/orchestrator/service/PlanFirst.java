package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Plan first: until the plan is approved, a session writes the files that make the plan and nothing else. */
public final class PlanFirst {

    private static final Set<Artifact> OPEN_WRITES = EnumSet.of(Artifact.PLAN, Artifact.VISUAL, Artifact.NOTES);

    private PlanFirst() {
    }

    /** Why a write to {@code file} is refused, or empty when it is allowed. */
    public static Optional<String> refusal(TaskState task, @Nullable String file) {
        if (!task.planGate().closed() || file == null || task.worktreePath() == null) {
            return Optional.empty();
        }
        Path root = Path.of(task.worktreePath()).normalize();
        Path target = root.resolve(file).normalize();
        boolean planning = OPEN_WRITES.stream().anyMatch(artifact -> target.equals(root.resolve(artifact.fileName())));
        return planning ? Optional.empty() : Optional.of("jagt refuses this write: plan first. List this file in "
                + Artifact.PLAN.fileName() + "; it is written once the plan is approved on the board.");
    }
}
