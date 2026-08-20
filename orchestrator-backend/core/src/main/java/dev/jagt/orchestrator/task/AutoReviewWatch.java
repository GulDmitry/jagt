package dev.jagt.orchestrator.task;

/**
 * Whether anything is polling this task's review request, and when it will next look — a poll happens with nobody
 * watching, so silence must not be the only answer a surface has.
 *
 * @param nextPollAt epoch millis of the next poll; 0 unless {@link State#WATCHING}. Absolute rather than a
 *                   remaining duration, because a page repaints its own clocks long after it fetched them
 * @param note       what a human is told, written ONCE here so the surfaces cannot drift apart in wording; null
 *                   when there is nothing to say. It names no topic: whoever shows it is already the poller's
 *                   own element or line, and a heading inside the sentence would be the second one in three
 *                   words. The countdown is not in it either — that is formatted per surface
 * @param label      the same state in the two or three words a chip holds; null while a countdown says it
 *                   better. It says what stopped, not which internal condition stopped it
 */
public record AutoReviewWatch(State state, long nextPollAt, String note, String label) {

    /**
     * {@link #NONE} covers every "there is nothing to poll here" case at once, including polling switched off for
     * the whole install — which a surface states once rather than per task. {@link #OFF_FOR_TASK} is the one
     * exception worth repeating on the task itself: it stays still while its neighbours are watched.
     */
    public enum State { NONE, WATCHING, WINDOW_ELAPSED, OFF_FOR_TASK, NO_ROUND }

    /**
     * Whether polling was expected here and has stopped, as opposed to an install that polls nothing at all —
     * {@link State#NONE} says the topic does not apply to this task, and the surfaces state the install-wide
     * answer once instead of on every card.
     */
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
