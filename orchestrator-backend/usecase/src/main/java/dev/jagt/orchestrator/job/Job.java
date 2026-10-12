package dev.jagt.orchestrator.job;

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

    /** A run never overlaps itself, but every job shares one pool. */
    void run();
}
