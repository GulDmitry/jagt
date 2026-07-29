package dev.jawo.orchestrator.model;

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
        if (message != null && message.toLowerCase().startsWith("awaiting")) {
            return "NEEDS INPUT: " + message.replaceFirst("(?i)^awaiting:?\\s*", "");
        }
        return switch (task.status()) {
            case CI_FAILED -> "PROBLEM: " + orDefault(message, "pipeline/build failed");
            case SHIPPING -> "SHIPPING: agent committing & pushing… (focus to watch)";
            case CI_POLLING, DEPLOYED -> orDefault(task.mrUrl(), "MR link missing");
            // The title now lives in its own dashboard column, so the detail line is contextual only:
            // the clickable MR link once one exists, nothing while still pre-MR.
            case REVIEW_PENDING -> hasMr(task) ? task.mrUrl() : "";
            case NEW, IN_PROGRESS, DONE -> "";
        };
    }

    private static boolean hasMr(TaskState task) {
        return task.mrUrl() != null && !task.mrUrl().isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
