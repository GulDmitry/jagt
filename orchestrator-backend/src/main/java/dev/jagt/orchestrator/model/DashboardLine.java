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
        boolean awaiting = message != null && message.toLowerCase().startsWith("awaiting");
        return switch (task.status()) {
            case CI_FAILED -> "PROBLEM: " + orDefault(message, "pipeline/build failed");
            case DEPLOY_CONFLICT -> "NEEDS YOU: " + orDefault(message, "deploy conflict — resolve in the deploy worktree");
            case SHIPPING -> "SHIPPING: agent committing & pushing… (focus to watch)";
            case CI_POLLING, REVIEWED, APPROVED, DEPLOYED -> orDefault(task.mrUrl(), "MR link missing");
            // A live MR is the clickable next step — show it even if the agent also left an "awaiting"
            // note; only fall back to NEEDS INPUT / blank when there is no MR yet. (The title lives in
            // its own dashboard column, so this line stays contextual.)
            case REVIEW_PENDING -> hasMr(task) ? task.mrUrl() : (awaiting ? needsInput(message) : "");
            case NEW, IN_PROGRESS -> awaiting ? needsInput(message) : "";
            case DONE -> "";
        };
    }

    private static String needsInput(String message) {
        return "NEEDS INPUT: " + message.replaceFirst("(?i)^awaiting:?\\s*", "");
    }

    private static boolean hasMr(TaskState task) {
        return task.mrUrl() != null && !task.mrUrl().isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
