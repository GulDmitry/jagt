package dev.jagt.orchestrator.stand;

import java.time.Duration;

final class RunEnd {

    static final Duration QUIET = Duration.ofSeconds(60);
    static final Duration SETTLED = Duration.ofSeconds(3);
    static final Duration HELD = Duration.ofMinutes(10);

    private final StandMode mode;
    private final boolean expectsDeployConflict;

    RunEnd(StandMode mode, boolean expectsDeployConflict) {
        this.mode = mode;
        this.expectsDeployConflict = expectsDeployConflict;
    }

    boolean held(Timeline timeline, boolean nothingToPress) {
        if (!timeline.lastOwner().equals("YOU")) {
            return false;
        }
        if (expectsDeployConflict && timeline.lastStatus().equals("DEPLOY_CONFLICT") && (timeline.relayed("conflict") || timeline.stalledFor(QUIET))) {
            return true;
        }
        return mode.humanEndsTheRun() && nothingToPress && timeline.stalledFor(QUIET) || timeline.stalledFor(HELD);
    }

    boolean reached(boolean untilReached, boolean eventsPending, Timeline timeline) {
        return untilReached && (!eventsPending || timeline.stalledFor(QUIET));
    }

    boolean waitingOnTheReviewer(boolean sweepsWaiting, Timeline timeline) {
        return sweepsWaiting && timeline.stalledFor(SETTLED);
    }
}
