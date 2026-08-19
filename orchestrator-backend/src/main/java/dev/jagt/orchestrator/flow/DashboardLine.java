package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/**
 * The one contextual line under a task — empty whenever the status and the next move already answer, so a
 * surface must expect nothing to render. Never the agent's own status chatter ("tests green"): what it says
 * about its progress is not what a human is owed here.
 */
public final class DashboardLine {

    private DashboardLine() {
    }

    public static String forTask(TaskState task) {
        String message = task.message();
        AgentReport report = AgentReport.of(message);
        return switch (task.status()) {
            case CI_FAILED -> "PROBLEM: " + orDefault(message, "checks failed");
            case DEPLOY_CONFLICT -> "NEEDS YOU: " + orDefault(message, "deploy conflict — resolve in the deploy worktree");
            case CI_POLLING, REVIEWED, APPROVED, DEPLOYED, REVERTED ->
                    orDefault(task.mrUrl(), "review request link missing");
            // A question OUTRANKS the request link: a link reads as "ready to ship", so the human ships and the
            // unanswered question goes out as a review reply.
            case REVIEW_PENDING -> switch (report) {
                case QUESTION -> needsInput(message);
                case NO_CHANGES -> "ANSWERED: " + orDefault(report.detailOf(message), "nothing to change")
                        + (hasMr(task) ? " · " + task.mrUrl() : "");
                case PLAIN -> hasMr(task) ? task.mrUrl() : "";
            };
            case NEW, IN_PROGRESS -> report == AgentReport.QUESTION ? needsInput(message) : "";
            case SHIPPING, DONE -> "";
        };
    }

    private static String needsInput(String message) {
        return "NEEDS INPUT: " + AgentReport.QUESTION.detailOf(message);
    }

    private static boolean hasMr(TaskState task) {
        return task.mrUrl() != null && !task.mrUrl().isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
