package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.task.TaskState;

@FunctionalInterface
public interface PhaseProfiles {

    /** What the Master may do on the task in the phase it stands in: none where it read the round or left the task. */
    Arbiter.Profile at(TaskState task);
}
