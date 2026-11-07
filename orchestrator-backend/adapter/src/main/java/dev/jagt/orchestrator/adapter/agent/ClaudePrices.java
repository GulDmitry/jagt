package dev.jagt.orchestrator.adapter.agent;

import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * List price per million tokens, by model id; a dated variant (`-YYYYMMDD`) takes its undated id's
 * price. https://claude.com/pricing#api — a model no row names prices nothing rather than something
 * wrong; Haiku 5.5 at its rate for prompts up to 100K tokens.
 */
@RequiredArgsConstructor
enum ClaudePrices {
  FABLE_5_1("claude-fable-5-1", 10, 0.25, 50),
  FABLE("claude-fable-5", 10, 1, 50),
  OPUS_5_5("claude-opus-5-5", 4, 0.2, 20),
  OPUS_4_1("claude-opus-4-1", 15, 1.5, 75),
  OPUS("claude-opus-5", 5, 0.5, 25),
  SONNET_5_5("claude-sonnet-5-5", 2, 0.1, 10),
  SONNET_5("claude-sonnet-5", 2, 0.2, 10),
  SONNET("claude-sonnet-4-6", 3, 0.3, 15),
  HAIKU_5_5("claude-haiku-5-5", 0.1, 0.01, 0.5),
  HAIKU("claude-haiku-4-5", 1, 0.1, 5);

  private static final double CACHE_WRITE_5M = 1.25;
  private static final double CACHE_WRITE_1H = 2;
  private static final double PER_MILLION = 1_000_000;
  private static final Pattern DATE_SUFFIX = Pattern.compile("-\\d{8}$");

  private final String model;
  private final double input;
  private final double cacheRead;
  private final double output;

  static Optional<ClaudePrices> of(@Nullable String model) {
    if (model == null) {
      return Optional.empty();
    }
    String id = DATE_SUFFIX.matcher(model).replaceFirst("");
    for (ClaudePrices prices : values()) {
      if (prices.model.equals(id)) {
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
            + output * this.output)
        / PER_MILLION;
  }
}
