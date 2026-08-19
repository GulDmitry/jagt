package dev.jagt.orchestrator.task;

/**
 * Whether anything is polling this task's review request, and when it will next look. A poll happens with nobody
 * watching, so a surface that cannot say this leaves a human guessing whether the silence means "waiting" or
 * "nothing is running".
 *
 * @param nextPollAt epoch millis of the next poll; 0 unless {@link State#WATCHING}. Absolute rather than a
 *                   remaining duration, because a page repaints its own clocks long after it fetched them
 * @param note       what a human is told, written ONCE here so the two surfaces cannot drift apart in wording;
 *                   null when there is nothing to say. Only the countdown is formatted per surface — it ticks
 *                   between fetches, so a rendered duration would be stale
 */
public record AutoReviewWatch(State state, long nextPollAt, String note) {

    /**
     * {@link #NONE} covers every "there is nothing to poll here" case at once — a task not out for review, one
     * with no request yet, and polling switched off for the whole install, which every surface announces once
     * rather than per task. {@link #OFF_FOR_TASK} is the one exception worth repeating on the task itself: a task
     * created while polling was off keeps its own answer, so it stays still while its neighbours are watched, and
     * {@link State#NO_ROUND} the other: a request whose round was never stamped can never be timed.
     */
    public enum State { NONE, WATCHING, WINDOW_ELAPSED, OFF_FOR_TASK, NO_ROUND }

    public static AutoReviewWatch none() {
        return new AutoReviewWatch(State.NONE, 0, null);
    }

    public static AutoReviewWatch watching(long nextPollAt) {
        return new AutoReviewWatch(State.WATCHING, nextPollAt, "auto-review · next poll");
    }

    public static AutoReviewWatch windowElapsed() {
        return new AutoReviewWatch(State.WINDOW_ELAPSED, 0,
                "auto-review · window elapsed — nothing polls this any more; sweep it");
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
