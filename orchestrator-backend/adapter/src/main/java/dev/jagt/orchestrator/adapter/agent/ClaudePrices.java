package dev.jagt.orchestrator.adapter.agent;

import java.util.Optional;

/**
 * List price per million tokens, by model family. https://claude.com/pricing#api — a model no row names prices
 * nothing rather than something wrong.
 */
enum ClaudePrices {

    FABLE_5_1("fable-5-1", 10, 0.25, 50),
    FABLE("fable", 10, 1, 50),
    OPUS_4_1("opus-4-1", 15, 1.5, 75),
    OPUS("opus", 5, 0.5, 25),
    SONNET_5("sonnet-5", 2, 0.2, 10),
    SONNET("sonnet", 3, 0.3, 15),
    HAIKU("haiku", 1, 0.1, 5);

    private static final double CACHE_WRITE_5M = 1.25;
    private static final double CACHE_WRITE_1H = 2;
    private static final double PER_MILLION = 1_000_000;

    private final String family;
    private final double input;
    private final double cacheRead;
    private final double output;

    ClaudePrices(String family, double input, double cacheRead, double output) {
        this.family = family;
        this.input = input;
        this.cacheRead = cacheRead;
        this.output = output;
    }

    /** Declaration order decides: the more specific family comes before the one its name contains. */
    static Optional<ClaudePrices> of(String model) {
        if (model == null) {
            return Optional.empty();
        }
        for (ClaudePrices prices : values()) {
            if (model.contains(prices.family)) {
                return Optional.of(prices);
            }
        }
        return Optional.empty();
    }

    double costOf(long input, long cacheWrite5m, long cacheWrite1h, long cacheRead, long output) {
        return (input * this.input
                + cacheWrite5m * this.input * CACHE_WRITE_5M
                + cacheWrite1h * this.input * CACHE_WRITE_1H
                + cacheRead * this.cacheRead
                + output * this.output) / PER_MILLION;
    }
}
