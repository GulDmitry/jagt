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

    public static String forTask(TaskState task, String usableRequestLink) {
        String message = task.message();
        AgentReport report = AgentReport.of(message);
        return switch (task.status()) {
            case CI_FAILED -> "PROBLEM: " + orDefault(message, "checks failed");
            case DEPLOY_CONFLICT -> "NEEDS YOU: " + orDefault(message, "deploy conflict; resolve it in the deploy worktree");
            case CI_POLLING, REVIEWED, APPROVED, DEPLOYED, REVERTED -> requestProblem(task, usableRequestLink);
            // A question OUTRANKS the request link: a link reads as "ready to ship", so the human ships and the
            // unanswered question goes out as a review reply.
            case REVIEW_PENDING -> switch (report) {
                case QUESTION -> needsInput(message);
                case NO_CHANGES -> "ANSWERED: " + orDefault(report.detailOf(message), "nothing to change");
                case PLAIN -> requestProblem(task, usableRequestLink);
            };
            case NEW, IN_PROGRESS -> report == AgentReport.QUESTION ? needsInput(message) : silence(task);
            case SHIPPING -> silence(task);
            case DONE -> "";
        };
    }

    /**
     * A request every surface links from needs no line of its own; one nothing can link to does. Only a web URL
     * can be followed, so a stored value that is not one leaves the task with a request and no way to reach it.
     */
    private static String requestProblem(TaskState task, String usableRequestLink) {
        if (!hasMr(task)) {
            return "";
        }
        return usableRequestLink == null ? "PROBLEM: review request link unusable: " + task.mrUrl() : "";
    }

    /**
     * The status says the agent is working and the watchdog found otherwise — the one case where the status
     * itself misleads, so it is shouted rather than left to the next-move line.
     */
    private static String silence(TaskState task) {
        return task.agentIsSilent() ? "NEEDS YOU: agent stopped: no MCP call and no process in its window" : "";
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
