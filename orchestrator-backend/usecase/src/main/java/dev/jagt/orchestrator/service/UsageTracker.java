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
 * Books what jagt's own model calls cost. Booking is TWO steps because a call can happen before the task it belongs
 * to exists: {@link #record} takes the session total, {@link #chargeTask} attributes the same measurement once
 * there is a task. The two totals are NOT summable — the session's counts retired tasks too.
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
     * Attributes an already-recorded measurement to a task, by id or alias. A task that is gone loses only its
     * per-task detail, but is still logged, the same silence otherwise hiding a wrong id.
     */
    public void chargeTask(String taskId, TokenUsage usage) {
        if (usage == null || usage.isNone() || taskId == null || taskId.isBlank()) {
            return;
        }
        String canonical = stateService.canonicalTaskId(taskId);
        if (!stateService.updateTask(canonical, t -> t.withUsageAdded(usage))) {
            log.atWarn().setMessage("task charge skipped")
                    .addKeyValue("task", canonical)
                    .addKeyValue("tokens", usage.total())
                    .addKeyValue("cause", "not in state.json")
                    .addKeyValue("effect", "session total only")
                    .log();
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
