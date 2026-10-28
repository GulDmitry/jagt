package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
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
    private final Clock clock;
    private final Map<String, Long> alertedSince = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "watchdog";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
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
        return FlowRules.watched(status);
    }

    @Override
    public void run() {
        long now = clock.millis();
        Map<String, TaskState> tasks = stateService.tasks();
        probe.keepOnly(tasks.keySet());
        tasks.forEach((taskId, task) -> verdict(taskId, task, now));
    }

    /** One task on its own, for a session that just reported itself and must not wait out the interval. */
    public void check(String taskId) {
        stateService.task(taskId).ifPresent(task -> verdict(taskId, task, clock.millis()));
    }

    /** An agent that exited without a word from its harness, told by what it printed last. */
    public void ended(String taskId, String printed) {
        probe.report(taskId, SessionProbe.State.GONE, printed.isBlank() ? null : OneLine.clipped(printed), clock.millis());
        check(taskId);
    }

    /** A session sitting at a prompt that fires no hook, told by what it shows. */
    public void waiting(String taskId, String cause) {
        probe.report(taskId, SessionProbe.State.WAITING, cause, clock.millis());
        check(taskId);
    }

    private void verdict(String taskId, TaskState task, long now) {
        long staleMs = properties.watchdog().staleAfter().toMillis();
        Optional<SessionProbe.Silence> silence = watches(task.status())
                ? probe.of(taskId, task, staleMs, now)
                : Optional.empty();
        stamp(taskId, task, silence.orElse(null));
        if (silence.isEmpty()) {
            alertedSince.remove(taskId);
            return;
        }
        // The wait FOLLOWING an agent's own question was announced when it asked.
        boolean announcedWhenItAsked = silence.get().atAPrompt()
                && task.report().asks();
        if (announcedWhenItAsked || Objects.equals(alertedSince.get(taskId), silence.get().since())) {
            return;
        }
        String said = body(silence.get());
        alertedSince.put(taskId, silence.get().since());
        log.atWarn().setMessage("task stopped")
                .addKeyValue("task", taskId)
                .addKeyValue("said", OneLine.clipped(said))
                .addKeyValue("cause", cause(silence.get()))
                .addKeyValue("silent", (now - silence.get().since()) / 60_000)
                .log();
        notifications.send(Notification.watchdog(taskId, "agent stopped", said));
    }

    private static String cause(SessionProbe.Silence silence) {
        if (silence.cause() != null) {
            return silence.cause();
        }
        return silence.reported() == null ? "silent" : silence.reported().name().toLowerCase(Locale.ROOT);
    }

    /** A banner has one line and no clock, so the absence of any word about a session has to become one. */
    private static String body(SessionProbe.Silence silence) {
        return silence.detail() == null ? "no sign of life" : silence.detail();
    }

    /**
     * Written only when the verdict CHANGES: every surface repaints on a state write. Keeping the first detection's
     * timestamp is what makes the duration grow instead of resetting.
     */
    void stamp(String taskId, TaskState task, SessionProbe.@Nullable Silence silence) {
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
