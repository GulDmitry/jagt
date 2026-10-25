package dev.jagt.orchestrator.stand;

import java.time.Duration;

final class RunEnd {

    static final Duration QUIET = Duration.ofSeconds(60);
    static final Duration HELD = Duration.ofMinutes(10);

    private final StandMode mode;

    RunEnd(StandMode mode) {
        this.mode = mode;
    }

    boolean held(Timeline timeline, boolean nothingToPress) {
        if (!timeline.lastOwner().equals("YOU")) {
            return false;
        }
        if (timeline.lastStatus().equals("DEPLOY_CONFLICT") && (timeline.relayed("conflict") || timeline.stalledFor(QUIET))) {
            return true;
        }
        return mode.humanEndsTheRun() && nothingToPress && timeline.stalledFor(QUIET) || timeline.stalledFor(HELD);
    }
}
