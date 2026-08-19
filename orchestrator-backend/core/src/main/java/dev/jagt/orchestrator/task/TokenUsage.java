package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What jagt's own model calls cost. A sub-agent's spend lives in its own session where jagt cannot see it, so
 * this is never a task's total cost.
 *
 * <p>{@code inputTokens} counts fresh context, cache writes included — both bill at input rates;
 * {@code cachedInputTokens} counts cache reads, an order of magnitude cheaper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenUsage(int calls, long inputTokens, long cachedInputTokens, long outputTokens,
                         double costUsd) {

    public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0, 0);

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
                costUsd + other.costUsd);
    }

    /** Every token that passed through, fresh or cached, in or out. */
    @JsonIgnore
    public long total() {
        return inputTokens + cachedInputTokens + outputTokens;
    }

    /** {@code @JsonIgnore} keeps Jackson from persisting this derived accessor as a {@code "none"} field. */
    @JsonIgnore
    public boolean isNone() {
        return calls == 0;
    }
}
