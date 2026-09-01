package dev.jagt.orchestrator.task;

/**
 * Whether anything is polling this task's review request, and when it will next look. {@code nextPollAt} is epoch
 * millis, absolute rather than a remaining duration because a page repaints its own clocks long after it fetched
 * them; 0 unless {@link State#WATCHING}. {@code note} is the wording written once for every surface and carries no
 * countdown; {@code label} is the same state in the two or three words a chip holds. Both null when nothing to say.
 */
public record AutoReviewWatch(State state, long nextPollAt, String note, String label) {

    /**
     * {@link #NONE} covers every "nothing to poll here" case, including polling switched off install-wide.
     * {@link #OFF_FOR_TASK} is the one worth repeating per task: it stays still while its neighbours are watched.
     */
    public enum State { NONE, WATCHING, WINDOW_ELAPSED, OFF_FOR_TASK, NO_ROUND }

    /** Whether polling was expected here and has stopped, as opposed to an install that polls nothing at all. */
    public boolean stopped() {
        return switch (state) {
            case WINDOW_ELAPSED, OFF_FOR_TASK, NO_ROUND -> true;
            case NONE, WATCHING -> false;
        };
    }

    public static AutoReviewWatch none() {
        return new AutoReviewWatch(State.NONE, 0, null, null);
    }

    public static AutoReviewWatch watching(long nextPollAt) {
        return new AutoReviewWatch(State.WATCHING, nextPollAt, "next poll", null);
    }

    public static AutoReviewWatch windowElapsed(long windowHours) {
        return new AutoReviewWatch(State.WINDOW_ELAPSED, 0,
                "no further polls: this round is past its " + windowHours + "h window",
                "polling stopped");
    }

    public static AutoReviewWatch offForTask() {
        return new AutoReviewWatch(State.OFF_FOR_TASK, 0, "polling is disabled for this task",
                "polling off");
    }

    /** A request nothing can time — its round was never stamped, so no interval can be measured from it. */
    public static AutoReviewWatch noRound() {
        return new AutoReviewWatch(State.NO_ROUND, 0,
                "no round stamp, so no poll interval can be measured", "cannot time this");
    }
}
