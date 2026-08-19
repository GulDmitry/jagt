package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Books what jagt's own model calls cost, so the spend is visible instead of invisible.
 *
 * <p>Booking is TWO steps because a call can happen before the task it belongs to exists: {@link #record} takes
 * the session total the moment a call returns, {@link #chargeTask} attributes the same measurement once there is
 * a task. Charging twice is the one way a caller can get this wrong.
 *
 * <p>The two totals are NOT summable: a task's lives as long as the task, the session's counts every call since
 * startup — including retired tasks and reads that never became one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsageTracker {

    private final StateService stateService;
    /** Session spend split by what the call was FOR; the session total is the sum, never a second counter. */
    private final Map<AssistantCallKind, TokenUsage> sessionByKind = new ConcurrentHashMap<>();

    /** Adds a measured call to the session total. Call this for EVERY call, attributable or not. */
    public void record(AssistantCallKind kind, TokenUsage usage) {
        if (usage == null || usage.isNone() || kind == null) {
            return;
        }
        sessionByKind.merge(kind, usage, TokenUsage::plus);
    }

    /**
     * Attributes an already-recorded measurement to a task, by id or alias. A task that is gone (retired
     * mid-sweep) only loses its per-task detail — the session total already has the number — but that is still
     * worth a line in the log, because the same silence would otherwise hide a wrong id.
     */
    public void chargeTask(String taskId, TokenUsage usage) {
        if (usage == null || usage.isNone() || taskId == null || taskId.isBlank()) {
            return;
        }
        String canonical = stateService.canonicalTaskId(taskId);
        if (!stateService.updateTask(canonical, t -> t.withUsageAdded(usage))) {
            log.warn("Cannot charge {} tokens to task {}: not in state.json (retired?) — it stays in the"
                    + " session total only", usage.total(), canonical);
        }
    }

    public TokenUsage session() {
        return sessionByKind.values().stream().reduce(TokenUsage.NONE, TokenUsage::plus);
    }

    public Map<AssistantCallKind, TokenUsage> sessionByKind() {
        Map<AssistantCallKind, TokenUsage> copy = new EnumMap<>(AssistantCallKind.class);
        copy.putAll(sessionByKind);
        return copy;
    }
}
