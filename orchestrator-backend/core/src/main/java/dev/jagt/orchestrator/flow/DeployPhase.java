package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;
import org.jspecify.annotations.Nullable;

/** The deploy region's states: none, landing, or handed to a session at a conflict. */
public enum DeployPhase {
    NONE, LANDING, HANDED;

    public static DeployPhase of(@Nullable Deployment deployment) {
        if (deployment == null) {
            return NONE;
        }
        boolean handed = deployment.at() < deployment.targets().size()
                && deployment.targets().get(deployment.at()).conflicted();
        return handed ? HANDED : LANDING;
    }
}
