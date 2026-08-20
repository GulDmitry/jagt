package dev.jagt.orchestrator.task;

/**
 * Whether anything is polling this task's review request, and when it will next look — a poll happens with nobody
 * watching, so silence must not be the only answer a surface has.
 *
 * @param nextPollAt epoch millis of the next poll; 0 unless {@link State#WATCHING}. Absolute rather than a
 *                   remaining duration, because a page repaints its own clocks long after it fetched them
 * @param note       what a human is told, written ONCE here so the surfaces cannot drift apart in wording; null
 *                   when there is nothing to say. The countdown is not in it — that is formatted per surface
 */
public record AutoReviewWatch(State state, long nextPollAt, String note) {

    /**
     * {@link #NONE} covers every "there is nothing to poll here" case at once, including polling switched off for
     * the whole install — which a surface states once rather than per task. {@link #OFF_FOR_TASK} is the one
     * exception worth repeating on the task itself: it stays still while its neighbours are watched.
     */
    public enum State { NONE, WATCHING, WINDOW_ELAPSED, OFF_FOR_TASK, NO_ROUND }

    public static AutoReviewWatch none() {
        return new AutoReviewWatch(State.NONE, 0, null);
    }

    public static AutoReviewWatch watching(long nextPollAt) {
        return new AutoReviewWatch(State.WATCHING, nextPollAt, "auto-review · next poll");
    }

    public static AutoReviewWatch windowElapsed(long windowHours) {
        return new AutoReviewWatch(State.WINDOW_ELAPSED, 0, "auto-review · " + stoppedPolling(windowHours));
    }

    /** The same sentence for a surface that has its own heading for the topic. */
    public static String stoppedPolling(long windowHours) {
        return "stopped polling this round after " + windowHours + "h; sweep it yourself";
    }

    public static AutoReviewWatch offForTask() {
        return new AutoReviewWatch(State.OFF_FOR_TASK, 0,
                "auto-review · off for this task; sweep it yourself");
    }

    /** A request nothing can time — its round was never stamped, so no interval can be measured from it. */
    public static AutoReviewWatch noRound() {
        return new AutoReviewWatch(State.NO_ROUND, 0,
                "auto-review · cannot time this round (no stamp); sweep it yourself");
    }
}
