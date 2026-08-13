package dev.jagt.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What jagt's own model calls cost: the headless assistant spend, accumulated per task in state.json and
 * per session in memory. Only MASTER-side calls land here — a sub-agent's spend lives in its own Claude
 * session and jagt cannot see it, so this is jagt's cost of running a task, not the task's total cost.
 *
 * <p>{@code inputTokens} counts fresh context (prompt + cache writes, both billed at input rates);
 * {@code cachedInputTokens} counts cache reads, kept apart because they are an order of magnitude cheaper
 * and their share is the signal for "is the prompt cache doing anything for us".
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

    /** Every token that passed through, fresh or cached, in or out — the one number a column can hold. */
    @JsonIgnore
    public long total() {
        return inputTokens + cachedInputTokens + outputTokens;
    }

    /** Nothing measured — nothing to show and nothing to add.
     *  {@code @JsonIgnore}: Jackson would otherwise persist this derived accessor as a {@code "none"}
     *  field in state.json, which is the SSOT and must carry state only. */
    @JsonIgnore
    public boolean isNone() {
        return calls == 0;
    }
}
