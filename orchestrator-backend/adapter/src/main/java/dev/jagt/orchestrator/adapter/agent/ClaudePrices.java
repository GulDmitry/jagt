package dev.jagt.orchestrator.adapter.agent;

import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * List price per million tokens, by model id; a dated variant (`-YYYYMMDD`) takes its undated id's
 * price. https://claude.com/pricing#api — a model no row names prices nothing rather than something
 * wrong. A row with a long-context rate charges it for a call whose whole prompt (input, cache
 * writes and cache reads) exceeds the row's threshold.
 */
enum ClaudePrices {
  FABLE_5_1("claude-fable-5-1", "2026-11-08", rate(10, 12.5, 20, 0.25, 50)),
  FABLE("claude-fable-5", "2026-11-08", rate(10, 12.5, 20, 1, 50)),
  OPUS_5_5("claude-opus-5-5", "2026-11-08", rate(4, 5, 8, 0.2, 20)),
  OPUS_4_1("claude-opus-4-1", "unverified", rate(15, 18.75, 30, 1.5, 75)),
  OPUS("claude-opus-5", "2026-11-08", rate(5, 6.25, 10, 0.5, 25)),
  SONNET_5_5("claude-sonnet-5-5", "2026-11-08", rate(2, 2.5, 4, 0.1, 10)),
  SONNET_5("claude-sonnet-5", "2026-11-08", rate(2, 2.5, 4, 0.2, 10)),
  SONNET("claude-sonnet-4-6", "2026-11-08", rate(3, 3.75, 6, 0.3, 15)),
  HAIKU_5_5(
      "claude-haiku-5-5",
      "2026-11-08",
      rate(0.1, 0.125, 0.2, 0.01, 0.5),
      rate(0.5, 0.625, 1, 0.05, 2.5),
      100_000),
  HAIKU("claude-haiku-4-5", "2026-11-08", rate(1, 1.25, 2, 0.1, 5));

  private static final double PER_MILLION = 1_000_000;
  private static final Pattern DATE_SUFFIX = Pattern.compile("-\\d{8}$");

  private final String model;
  @Getter(AccessLevel.PACKAGE)
  private final String checked;
  private final Rate standard;
  private final Rate longContext;
  private final long longContextAbove;

  ClaudePrices(String model, String checked, Rate rate) {
    this(model, checked, rate, rate, Long.MAX_VALUE);
  }

  ClaudePrices(
      String model, String checked, Rate standard, Rate longContext, long longContextAbove) {
    this.model = model;
    this.checked = checked;
    this.standard = standard;
    this.longContext = longContext;
    this.longContextAbove = longContextAbove;
  }

  @Immutable
  private record Rate(
      double input, double cacheWrite5m, double cacheWrite1h, double cacheRead, double output) {}

  private static Rate rate(
      double input, double cacheWrite5m, double cacheWrite1h, double cacheRead, double output) {
    return new Rate(input, cacheWrite5m, cacheWrite1h, cacheRead, output);
  }

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
    long prompt = input + cacheWrite5m + cacheWrite1h + cacheRead;
    Rate rate = prompt > longContextAbove ? longContext : standard;
    return (input * rate.input
            + cacheWrite5m * rate.cacheWrite5m
            + cacheWrite1h * rate.cacheWrite1h
            + cacheRead * rate.cacheRead
            + output * rate.output)
        / PER_MILLION;
  }
}
