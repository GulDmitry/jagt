package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.flow.SessionPhase;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Reads every open task's session each tick and alerts for the ones that have stopped in a status where the agent
 * should be working. What it finds is the session record's, written by the reducer alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchdogService implements Job {

    private static final String ALERTED = "alerted:silence";

    private final StateService stateService;
    private final Notifications notifications;
    private final OrchestratorProperties properties;
    private final SessionProbe probe;
    private final Clock clock;

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
        long now = clock.millis();
        SessionSign.Ended sign = new SessionSign.Ended(printed.isBlank() ? null : OneLine.clipped(printed));
        stateService.updateTask(taskId, task -> TaskReducer.signed(task, sign, now));
        check(taskId);
    }

    private void verdict(String taskId, TaskState task, long now) {
        if (FlowRules.closed(task.status())) {
            return;
        }
        long staleMs = properties.watchdog().staleAfter().toMillis();
        SessionSign.Read read;
        try {
            read = probe.read(taskId, task, staleMs, now);
        } catch (SessionHost.WindowsUnreadable unknown) {
            return;
        }
        stateService.updateTask(taskId, current -> TaskReducer.signed(current, read, now));
        alert(taskId, stateService.task(taskId).orElse(task), now);
    }

    private void alert(String taskId, TaskState task, long now) {
        if (!watches(task.status()) || !SessionPhase.silent(task)) {
            forgetAlert(taskId, task);
            return;
        }
        long since = SessionPhase.silentSince(task);
        // The wait FOLLOWING an agent's own question was announced when it asked.
        if (stillAsking(task) || task.turnOrNone().marked(ALERTED, String.valueOf(since))) {
            return;
        }
        String said = Objects.requireNonNullElse(SessionPhase.silentBecause(task), "no sign of life");
        stateService.updateTask(taskId, current -> current.withMark(ALERTED, String.valueOf(since)));
        log.atWarn().setMessage("task stopped")
                .addKeyValue("task", taskId)
                .addKeyValue("said", OneLine.clipped(said))
                .addKeyValue("cause", cause(task.session()))
                .addKeyValue("silent", (now - since) / 60_000)
                .log();
        notifications.send(Notification.watchdog(taskId, "agent stopped", said));
    }

    private static boolean stillAsking(TaskState task) {
        Session.Halt halt = task.session().halt();
        return task.report().asks() && (halt == null || halt.kind() == Session.Halt.Kind.IDLE);
    }

    private void forgetAlert(String taskId, TaskState task) {
        if (task.turnOrNone().mark(ALERTED) != null) {
            stateService.updateTask(taskId, current -> current.withoutMark(ALERTED));
        }
    }

    private static String cause(Session session) {
        Session.Halt halt = session.halt();
        if (halt == null) {
            return "silent";
        }
        return halt.cause() != null ? halt.cause() : halt.kind().name().toLowerCase(Locale.ROOT);
    }
}
