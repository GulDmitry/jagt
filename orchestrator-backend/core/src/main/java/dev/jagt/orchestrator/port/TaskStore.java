package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TaskState;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * What the machine needs of wherever tasks are kept: find one, and change one. Declared here so the rules and the
 * engine can be exercised without a filesystem, and so nothing in them knows how a task is stored.
 */
public interface TaskStore {

    /** The task, or empty when nothing by that id is registered. */
    Optional<TaskState> task(String taskId);

    /** The id a short alias stands for, or the argument itself when it is already an id. */
    String canonicalTaskId(String idOrAlias);

    /** Applies {@code update} to the task and persists it; false when there was no such task. */
    boolean updateTask(String taskId, UnaryOperator<TaskState> update);
}
