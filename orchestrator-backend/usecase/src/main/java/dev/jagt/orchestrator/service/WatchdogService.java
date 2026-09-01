package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the sessions that have stopped rather than the ones that are merely busy. What it finds is STAMPED on the
 * task, not only sent as a desktop ping, and this is the only place that stamp is written.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchdogService implements Job {

    private final StateService stateService;
    private final Notifications notifications;
    private final OrchestratorProperties properties;
    private final SessionProbe probe;
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "watchdog";
    }

    @Override
    public String describe() {
        return "alert when an agent has gone quiet in a status where it is supposed to be working";
    }

    @Override
    public Duration every() {
        return probe.every();
    }

    /**
     * The statuses where the agent is EXPECTED to be doing something, so silence means death — NEW included, for an
     * agent that died before its first status update. Every other status idles BY DESIGN and stays unwatched.
     */
    static boolean watches(TaskStatus status) {
        return status == TaskStatus.NEW || status == TaskStatus.IN_PROGRESS || status == TaskStatus.SHIPPING;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Map<String, TaskState> tasks = stateService.tasks();
        probe.keepOnly(tasks.keySet());
        tasks.forEach((taskId, task) -> verdict(taskId, task, now));
    }

    /** One task on its own, for a session that just reported itself and must not wait out the interval. */
    public void check(String taskId) {
        stateService.task(taskId).ifPresent(task -> verdict(taskId, task, System.currentTimeMillis()));
    }

    private void verdict(String taskId, TaskState task, long now) {
        long staleMs = properties.watchdog().staleAfter().toMillis();
        Optional<SessionProbe.Silence> silence = watches(task.status())
                ? probe.of(taskId, task, staleMs, now)
                : Optional.empty();
        stamp(taskId, task, silence.orElse(null));
        if (silence.isEmpty()) {
            lastAlertAt.remove(taskId);
            return;
        }
        // The wait FOLLOWING an agent's own question was announced when it asked.
        boolean announcedWhenItAsked = silence.get().atAPrompt()
                && AgentReport.of(task.message()) == AgentReport.QUESTION;
        // Re-alert at most once per stale window to avoid notification spam.
        if (announcedWhenItAsked || now - lastAlertAt.getOrDefault(taskId, 0L) < staleMs) {
            return;
        }
        String said = body(silence.get());
        lastAlertAt.put(taskId, now);
        log.atWarn().setMessage("task stopped")
                .addKeyValue("task", taskId)
                .addKeyValue("said", said)
                .addKeyValue("cause", silence.get().reported())
                .addKeyValue("silent", (now - silence.get().since()) / 60_000)
                .log();
        notifications.send(Notification.watchdog(taskId, "agent stopped", said));
    }

    /** A banner has one line and no clock, so the absence of any word about a session has to become one. */
    private static String body(SessionProbe.Silence silence) {
        return silence.detail() == null ? "no sign of life" : silence.detail();
    }

    /**
     * Written only when the verdict CHANGES: every surface repaints on a state write. Keeping the first detection's
     * timestamp is what makes the duration grow instead of resetting.
     */
    void stamp(String taskId, TaskState task, SessionProbe.Silence silence) {
        String because = silence == null ? null : silence.detail();
        if (task.agentIsSilent() == (silence != null) && Objects.equals(task.silentBecause(), because)) {
            return;
        }
        long since = silence == null ? 0 : silence.since();
        // Only over the state this verdict was reached from: a report can land on its own thread in between.
        stateService.updateTask(taskId, current -> unchanged(current, task)
                ? current.withSilentSince(since, because)
                : current);
    }

    private static boolean unchanged(TaskState current, TaskState decidedFrom) {
        return current.silentSince() == decidedFrom.silentSince()
                && Objects.equals(current.silentBecause(), decidedFrom.silentBecause());
    }
}
