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
                    checks(task) + orDefault(task.mrUrl(), "review request link missing");
            // A question OUTRANKS the request link: a link reads as "ready to ship", so the human ships and the
            // unanswered question goes out as a review reply.
            case REVIEW_PENDING -> switch (report) {
                case QUESTION -> needsInput(message);
                case NO_CHANGES -> "ANSWERED: " + orDefault(report.detailOf(message), "nothing to change")
                        + (hasMr(task) ? " · " + task.mrUrl() : "");
                case PLAIN -> hasMr(task) ? task.mrUrl() : "";
            };
            case NEW, IN_PROGRESS -> report == AgentReport.QUESTION ? needsInput(message) : silence(task);
            case SHIPPING -> silence(task);
            case DONE -> "";
        };
    }

    /**
     * The checks, only when they are not green: a red run while the task still reads CI_POLLING is what the status
     * alone cannot show.
     */
    private static String checks(TaskState task) {
        return switch (Pipeline.of(task.pipelineStatus())) {
            case RED -> "CHECKS RED · ";
            case RUNNING -> "checks running · ";
            case GREEN, NONE -> "";
        };
    }

    /**
     * The status says the agent is working and the watchdog found otherwise — the one case where the status
     * itself misleads, so it is shouted rather than left to the next-move line.
     */
    private static String silence(TaskState task) {
        return task.agentIsSilent() ? "NEEDS YOU: agent silent — no report and a quiet window" : "";
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
