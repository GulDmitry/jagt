package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;
import org.jspecify.annotations.Nullable;

/** The deploy region's states: none, landing, handed to a session at a conflict, being reverted, or its check asked. */
public enum DeployPhase {
    NONE, LANDING, HANDED, REVERTING, CHECK_ASKED;

    public static DeployPhase of(@Nullable Deployment deployment) {
        if (deployment == null) {
            return NONE;
        }
        boolean handed = deployment.at() < deployment.targets().size()
                && deployment.targets().get(deployment.at()).conflicted();
        if (handed) {
            return HANDED;
        }
        if (deployment.targets().stream().anyMatch(target -> target.revertCommit() != null)) {
            return REVERTING;
        }
        return deployment.askedDigest() == null ? LANDING : CHECK_ASKED;
    }
}
