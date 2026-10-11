package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tokens a model was handed and answered with. {@code inputTokens} counts fresh context, cache writes included —
 * both bill at input rates; {@code cachedInputTokens} counts cache reads, an order of magnitude cheaper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenUsage(int calls, long inputTokens, long cachedInputTokens, long outputTokens,
                         double costUsd, int coldStarts, int warmStarts) {

    public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0, 0);

    public TokenUsage(int calls, long inputTokens, long cachedInputTokens, long outputTokens, double costUsd) {
        this(calls, inputTokens, cachedInputTokens, outputTokens, costUsd, 0, 0);
    }

    /** One measured call. Zero tokens means the call never reached a model — that is NOT a call. */
    public static TokenUsage ofCall(long inputTokens, long cachedInputTokens, long outputTokens,
                                    double costUsd) {
        if (inputTokens == 0 && cachedInputTokens == 0 && outputTokens == 0) {
            return NONE;
        }
        return new TokenUsage(1, inputTokens, cachedInputTokens, outputTokens, costUsd);
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(calls + other.calls, inputTokens + other.inputTokens,
                cachedInputTokens + other.cachedInputTokens, outputTokens + other.outputTokens,
                costUsd + other.costUsd, coldStarts + other.coldStarts, warmStarts + other.warmStarts);
    }

    /** Marks one measured call as the start of a prompt prefix: cold when it read nothing from the cache. */
    public TokenUsage asStart() {
        if (isNone()) {
            return this;
        }
        boolean cold = cachedInputTokens == 0 && inputTokens > 0;
        return new TokenUsage(calls, inputTokens, cachedInputTokens, outputTokens, costUsd,
                cold ? 1 : 0, cold ? 0 : 1);
    }

    /** Starts whose cache fields were read; calls without them are in neither count, never counted warm. */
    @JsonIgnore
    public int measuredStarts() {
        return coldStarts + warmStarts;
    }

    /** Every token that passed through, fresh or cached, in or out. */
    @JsonIgnore
    public long total() {
        return inputTokens + cachedInputTokens + outputTokens;
    }

    /** {@code @JsonIgnore} so this derived accessor is not persisted as a {@code "none"} field. */
    @JsonIgnore
    public boolean isNone() {
        return calls == 0;
    }
}
