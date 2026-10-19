package dev.jagt.orchestrator.service;

/** Why act reads as judge. {@code ceiling} is the day's number under {@code DAILY_CEILING}, else 0. */
public record ActStop(Cause cause, int ceiling) {

    public enum Cause {
        STRIKES,
        DAILY_CEILING,
        /** The daily stop file exists and cannot be read; it stops as a standing stop does. */
        UNREADABLE
    }
}
