package dev.jagt.orchestrator.job;

import java.time.Duration;

/**
 * Work that runs with nobody watching. Declared rather than annotated, so "what does jagt do on its own" has an
 * answer in the code and on a screen — the same reason a capability is declared: an implementation that hides its
 * schedule inside itself cannot be listed, reported on, or validated against what it needs.
 */
public interface Job {

    /** Short, stable, lowercase: the name a human reads in the report and jagt logs it under. */
    String id();

    /** One line, in the terms of what the human gets out of it. */
    String describe();

    /** How often to run it; {@code null} means once, as soon as the application is up. */
    Duration every();

    /**
     * Runs it. May take as long as it takes — a run never overlaps itself — but every job shares one pool, so
     * work measured in minutes belongs on a thread of its own.
     */
    void run();
}
