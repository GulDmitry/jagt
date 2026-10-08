package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/**
 * The one contextual line under a task — empty whenever the status and the next move already answer, so a
 * surface must expect nothing to render. Never the agent's own status chatter ("tests green"): what it says
 * about its progress is not what a human is owed here.
 */
public final class DashboardLine {

    /** What the line is, so no reader parses its words: broken, the human's move, or a plain note. */
    public enum Kind { NONE, PROBLEM, YOURS, NOTE }

    public record Line(Kind kind, String text) {

        static final Line NONE = new Line(Kind.NONE, "");
    }

    private DashboardLine() {
    }

    public static Line forTask(TaskState task, String usableRequestLink) {
        String message = task.message();
        AgentReport report = AgentReport.of(message);
        // A question OUTRANKS every other line and is reachable from every status — an agent may ask without
        // moving its task.
        if (report == AgentReport.QUESTION && task.status() != TaskStatus.DONE) {
            return needsInput(message);
        }
        return switch (task.status()) {
            case CI_FAILED -> problem(orDefault(message, "checks failed"));
            case DEPLOY_CONFLICT -> new Line(Kind.YOURS,
                    "NEEDS YOU: " + orDefault(message, "deploy conflict; resolve it in the deploy worktree"));
            case CI_POLLING, REVIEWED, APPROVED, DEPLOYED, REVERTED -> requestProblem(task, usableRequestLink);
            case REVIEW_PENDING -> switch (report) {
                // The one round that leaves no highlighted button, so this line has to say whose move it is.
                case NO_CHANGES -> new Line(Kind.NOTE, "ANSWERED: "
                        + orDefault(report.detailOf(message), "nothing to change")
                        + " — the open threads are the reviewer's to close");
                case QUESTION, PLAIN -> requestProblem(task, usableRequestLink);
            };
            case NEW, IN_PROGRESS, SHIPPING -> silence(task);
            case PLAN_PENDING, VERIFYING, DONE -> Line.NONE;
        };
    }

    /**
     * A request every surface links from needs no line of its own; one nothing can link to does. Only a web URL
     * can be followed, so a stored value that is not one leaves the task with a request and no way to reach it.
     */
    private static Line requestProblem(TaskState task, String usableRequestLink) {
        if (!hasMr(task)) {
            return Line.NONE;
        }
        return usableRequestLink == null ? problem("review request link unusable: " + task.mrUrl()) : Line.NONE;
    }

    /**
     * The status says the agent is working and the watchdog found otherwise — the one case where the status itself
     * misleads. A session that DID report is quoted whatever its status.
     */
    private static Line silence(TaskState task) {
        if (!task.agentIsSilent()) {
            return Line.NONE;
        }
        String because = task.silentBecause();
        if (because == null || because.isBlank()) {
            because = task.status() == TaskStatus.NEW
                    ? "the agent never reported — check that the CLI started"
                    : "nothing has moved in its session";
        }
        return new Line(Kind.YOURS, "NEEDS YOU: agent stopped: " + because);
    }

    private static Line needsInput(String message) {
        return new Line(Kind.YOURS,
                "NEEDS INPUT: " + orDefault(AgentReport.QUESTION.detailOf(message), "the agent is waiting on you"));
    }

    private static Line problem(String text) {
        return new Line(Kind.PROBLEM, "PROBLEM: " + text);
    }

    private static boolean hasMr(TaskState task) {
        return task.mrUrl() != null && !task.mrUrl().isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
