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
        // A question OUTRANKS every other line and is reachable from every status — an agent may ask without
        // moving its task. A request link reads as "ready to ship", so the human ships and the unanswered
        // question goes out as a review reply.
        if (report == AgentReport.QUESTION && task.status() != TaskStatus.DONE) {
            return needsInput(message);
        }
        return switch (task.status()) {
            case CI_FAILED -> "PROBLEM: " + orDefault(message, "checks failed");
            case DEPLOY_CONFLICT -> "NEEDS YOU: " + orDefault(message, "deploy conflict; resolve it in the deploy worktree");
            case CI_POLLING, REVIEWED, APPROVED, DEPLOYED, REVERTED -> requestProblem(task, usableRequestLink);
            case REVIEW_PENDING -> switch (report) {
                // Complete on its own: this is the one round that leaves no highlighted button, so a surface
                // that shows only this line still has to say whose move it is.
                case NO_CHANGES -> "ANSWERED: " + orDefault(report.detailOf(message), "nothing to change")
                        + " — the open threads are the reviewer's to close";
                // A question never reaches here: it is answered above, from whatever status it was asked.
                case QUESTION, PLAIN -> requestProblem(task, usableRequestLink);
            };
            case NEW, IN_PROGRESS, SHIPPING -> silence(task);
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
     *
     * <p>Where nothing was reported about the session there is no sentence to carry, so this one is the
     * status's to make: at NEW the agent has not reported at all, and the launch is what a human should be
     * looking at rather than the agent. A session that DID report is quoted whatever its status.
     */
    private static String silence(TaskState task) {
        if (!task.agentIsSilent()) {
            return "";
        }
        String because = task.silentBecause();
        if (because == null || because.isBlank()) {
            because = task.status() == TaskStatus.NEW
                    ? "the agent never reported — check that the CLI started"
                    : "nothing has moved in its session";
        }
        return "NEEDS YOU: agent stopped: " + because;
    }

    private static String needsInput(String message) {
        return "NEEDS INPUT: " + orDefault(AgentReport.QUESTION.detailOf(message), "the agent is waiting on you");
    }

    private static boolean hasMr(TaskState task) {
        return task.mrUrl() != null && !task.mrUrl().isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
