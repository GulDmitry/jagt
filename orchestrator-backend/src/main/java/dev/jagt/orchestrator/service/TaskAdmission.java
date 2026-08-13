package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskState;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admission control: how many tasks may exist at once. Every agent is a full CLI session with its own
 * language server (1-2 GB for a Java worktree, per CLAUDE.md's resource hygiene) plus a worktree checkout —
 * five tasks is a different machine than two, and the human used to find out by watching everything crawl.
 *
 * <p>Statics with no collaborators on purpose (like {@link WorktreeFiles}): the two facts it needs are already
 * loaded by every caller, so a bean here would only add a constructor parameter to the class that creates
 * tasks. The cap itself lives in {@code agent.maxConcurrentTasks}, read through
 * {@link ConfigService.ConfigFile.AgentConfig#maxConcurrentTasksOrDefault()} — that accessor is what both
 * headers show, so the number displayed and the number enforced cannot drift.
 *
 * <p>A slot is held by a REGISTERED task, whatever its status: the worktree and the language server survive
 * every status up to and including DEPLOYED, and only {@code done} deletes them. Counting "running agents"
 * instead would free a slot the moment an agent exits and hand it to a new task while the old worktree — and
 * its copied secrets — are still on disk.
 *
 * <p>It refuses rather than queues (see TODO.md): queueing needs a pre-NEW status and a scheduler, and a
 * refusal with the reason is already the difference between a bounded machine and an unbounded one.
 */
public final class TaskAdmission {

    private TaskAdmission() {
    }

    /**
     * Throws with the sentence a human needs (what is full, what frees it, where the number lives) when a new
     * task would exceed the cap. Call before any git work, so a refusal costs nothing to undo.
     *
     * @param capacity the configured cap; 0 or negative means the human opted out and nothing is enforced
     */
    public static void requireSlot(String taskId, int capacity, Map<String, TaskState> tasks) {
        if (capacity <= 0 || tasks.size() < capacity) {
            return;
        }
        throw new IllegalArgumentException("Cannot start " + taskId + ": all " + capacity
                + " task slots are in use (" + occupants(tasks) + "). Each agent is a full session with its"
                + " own language server (1-2 GB), so jagt caps them — finish one and `done` it to free its"
                + " worktree, or raise agent.maxConcurrentTasks in config.json (0 = no cap).");
    }

    /** The tasks holding the slots, by alias where they have one — what the human has to act on. */
    private static String occupants(Map<String, TaskState> tasks) {
        return tasks.entrySet().stream()
                .map(TaskAdmission::label)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String label(Map.Entry<String, TaskState> task) {
        String alias = task.getValue().alias();
        return alias == null || alias.isBlank() ? task.getKey() : alias + " " + task.getKey();
    }
}
