package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Books what jagt's own model calls cost, so the spend is visible instead of invisible.
 *
 * <p>Booking is TWO steps on purpose, because a call can happen before the task it belongs to exists: the
 * {@code do} ticket read is what produces the id the task is then created under. So {@link #record} takes
 * the session total the moment the call returns (it can never be lost), and {@link #chargeTask} attributes
 * that same measurement once there is a task to attribute it to. Charging twice is the caller's only way to
 * get this wrong, and each call site does exactly one of each.
 *
 * <p>The two totals are NOT summable: a task's total lives as long as the task does, while the session
 * total counts every call since backend start — including tasks already retired with {@code done} and reads
 * that never became a task. The views label each for what it is.
 */
@Component
public class UsageTracker {

    private static final Logger log = LoggerFactory.getLogger(UsageTracker.class);

    private final StateService stateService;
    private final AtomicReference<TokenUsage> session = new AtomicReference<>(TokenUsage.NONE);

    public UsageTracker(StateService stateService) {
        this.stateService = stateService;
    }

    /** Adds a measured call to the session total. Call this for EVERY call, attributable or not. */
    public void record(TokenUsage usage) {
        if (usage == null || usage.isNone()) {
            return;
        }
        session.updateAndGet(current -> current.plus(usage));
    }

    /**
     * Attributes an already-recorded measurement to a task (id or alias), so the dashboard and {@code stats}
     * can show what that task has cost. A task that is gone (retired mid-sweep) only loses its per-task
     * detail — the session total already has the number — but that is worth a line in the log, because the
     * same silence would otherwise hide a wrong id.
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

    /** Everything spent since this backend started. */
    public TokenUsage session() {
        return session.get();
    }
}
