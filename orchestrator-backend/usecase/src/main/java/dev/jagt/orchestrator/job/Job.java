package dev.jagt.orchestrator.job;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/** Work that runs with nobody watching; declared rather than annotated so it can be listed and validated. */
public interface Job {

    /** Short, stable, lowercase. */
    String id();

    /** One line. */
    String describe();

    /** {@code null} means once, as soon as the application is up. */
    @Nullable Duration every();

    /** Whom a job works for. */
    @RequiredArgsConstructor
    enum Serves {
        TASKS("your tasks"), MASTER("the master"), TRACKER("the tracker"), HOUSEKEEPING("housekeeping");

        private final String label;

        public String label() {
            return label;
        }
    }

    default Serves serves() {
        return Serves.HOUSEKEEPING;
    }

    /** A run never overlaps itself, but every job shares one pool. */
    void run();
}
