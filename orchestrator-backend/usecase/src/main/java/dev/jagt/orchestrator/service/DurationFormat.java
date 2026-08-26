package dev.jagt.orchestrator.service;

/** How an elapsed time is written in a fixed-width column — one owner, shared by every view that shows one. */
public final class DurationFormat {

    private DurationFormat() {
    }

    /**
     * A countdown a human watches tick, unlike an age in a column: seconds matter while the wait is under a
     * minute, and "0m" would read as "now". Mirrored by the board's own formatter, which writes the same
     * countdown into a card.
     */
    public static String countdown(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        // CEILING, unlike an elapsed age: a wait of ten minutes must not read "9m" for its whole first minute.
        long minutes = (seconds + 59) / 60;
        return minutes < 60 ? minutes + "m" : (minutes + 59) / 60 + "h";
    }

    /** Coarse on purpose: one glance should say "minutes" or "days", and a second column of digits would not. */
    public static String compact(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        if (minutes < 60) {
            return minutes + "m";
        }
        return minutes < 60 * 24 ? minutes / 60 + "h" : minutes / (60 * 24) + "d";
    }
}
