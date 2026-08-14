package dev.jagt.orchestrator.model;

/**
 * The one-line detail shown under a task in the dashboard. Context-driven and
 * pure so it is deterministic and unit-tested, not improvised by the Master:
 * problems shout in CAPS, in-development tasks show the ticket title, tasks in
 * review show the clickable MR link. Agent status chatter ("tests green") is
 * intentionally NOT shown.
 */
public final class DashboardLine {

    private DashboardLine() {
    }

    public static String forTask(String taskId, TaskState task) {
        String message = task.message();
        AgentReport report = AgentReport.of(message);
        boolean awaiting = report == AgentReport.QUESTION;
        return switch (task.status()) {
            case CI_FAILED -> "PROBLEM: " + orDefault(message, "pipeline/build failed");
            case DEPLOY_CONFLICT -> "NEEDS YOU: " + orDefault(message, "deploy conflict — resolve in the deploy worktree");
            case SHIPPING -> "SHIPPING: agent committing & pushing… (focus to watch)";
            case CI_POLLING, REVIEWED, APPROVED, DEPLOYED, REVERTED ->
                    orDefault(task.mrUrl(), "MR link missing");
            // A question OUTRANKS the MR link: a review round can end with the agent asking instead of
            // guessing, and this line is the only place that question surfaces. Showing the link instead reads
            // as "ready to ship" — the human ships, and the unanswered question goes out as a review reply.
            // `awaiting:` is the prompt's reserved prefix for exactly this (rule 10), not status chatter. (The
            // title lives in its own column; this line stays contextual.)
            case REVIEW_PENDING -> switch (report) {
                case QUESTION -> needsInput(message);
                case NO_CHANGES -> "ANSWERED: " + orDefault(report.detailOf(message), "nothing to change")
                        + (hasMr(task) ? " · " + task.mrUrl() : "");
                case PLAIN -> hasMr(task) ? task.mrUrl() : "";
            };
            case NEW, IN_PROGRESS -> awaiting ? needsInput(message) : "";
            case DONE -> "";
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
