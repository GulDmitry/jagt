package dev.jagt.orchestrator.task;

/**
 * What a launch or a resume answers: the task it now has, null when it made none, and the sentence to show
 * a human.
 *
 * <p>The two are separate because most of the ways either declines are ordinary answers rather than failures —
 * an unreadable reference, a branch already there — and a surface that read them as success cleared the very
 * form holding the project, the branch and the instructions needed to try again.
 */
public record Launched(String taskId, String message) {

    public static Launched refused(String message) {
        return new Launched(null, message);
    }

    public static Launched created(String taskId, String message) {
        return new Launched(taskId, message);
    }

    public boolean created() {
        return taskId != null;
    }
}
