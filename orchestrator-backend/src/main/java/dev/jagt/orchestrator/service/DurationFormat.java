package dev.jagt.orchestrator.service;

/** How an elapsed time is written in a fixed-width column — one owner, shared by every view that shows one. */
public final class DurationFormat {

    private DurationFormat() {
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
