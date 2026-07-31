package dev.jawo.orchestrator.model;

/**
 * Whose move it is, and the command to run, for a task in a given status.
 * Pure and total over TaskStatus so the dashboard hint is deterministic and
 * unit-tested — not improvised by the Master LLM (which drifted, e.g. suggesting
 * `review` after a deploy). The Master renders this; it does not invent it.
 */
public final class NextMove {

    private NextMove() {
    }

    public static String forStatus(TaskStatus status) {
        return switch (status) {
            case NEW, IN_PROGRESS -> "agent working — wait or `focus`";
            case REVIEW_PENDING -> "your move: `ide` then `ship` (or `focus` to iterate)";
            case SHIPPING -> "shipping — agent committing/pushing; wait for the MR (or `focus`)";
            case CI_POLLING -> "your move: `review`";
            case CI_FAILED -> "your move: `review` (relays the failure)";
            case DEPLOYED -> "your move: `done`";
            case DONE -> "done";
        };
    }
}
