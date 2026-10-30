package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.task.TaskState;

import java.util.Map;

/** A job that acts on one task at a time, and only on the tasks its {@link #watch()} admits. */
public interface TaskJob extends Job {

    Watch watch();

    void run(String taskId, TaskState task);

    /** Hands {@link #run(String, TaskState)} each of {@code tasks} the watch admits, in the map's order. */
    default void runOn(Map<String, TaskState> tasks) {
        Watch watch = watch();
        tasks.forEach((taskId, task) -> {
            if (watch.admits(task)) {
                run(taskId, task);
            }
        });
    }

    @Override
    default void run() {
        throw new UnsupportedOperationException(id() + " is handed its tasks, not run bare");
    }
}
