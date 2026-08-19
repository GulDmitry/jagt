package dev.jagt.orchestrator.service;

import java.util.Locale;

/** How a token count is written in a fixed-width column — one owner, shared by every view that shows one. */
public final class TokenFormat {

    private TokenFormat() {
    }

    /**
     * {@code 812}, {@code 4.8k}, {@code 64k}, {@code 1.2M} — at most six characters. One decimal only
     * below ten units, where it still carries information; a value that would round up to the next unit
     * (999 600 → {@code 1.0M}) crosses over rather than printing a misleading {@code 1000k}.
     *
     * <p>Locale.ROOT on purpose: the default locale would print "4,8k" on a comma-decimal machine, which
     * reads as two numbers inside a table.
     */
    public static String compact(long tokens) {
        if (tokens < 1_000) {
            return String.valueOf(tokens);
        }
        if (tokens < 999_500) {
            // The cutoffs are where %.1f would ROUND UP into two digits ("10.0k"), not a round 10 000 —
            // otherwise the one-decimal form leaks past the range it is meant for.
            return tokens < 9_950
                    ? String.format(Locale.ROOT, "%.1fk", tokens / 1_000d)
                    : Math.round(tokens / 1_000d) + "k";
        }
        return tokens < 9_950_000
                ? String.format(Locale.ROOT, "%.1fM", tokens / 1_000_000d)
                : Math.round(tokens / 1_000_000d) + "M";
    }
}
