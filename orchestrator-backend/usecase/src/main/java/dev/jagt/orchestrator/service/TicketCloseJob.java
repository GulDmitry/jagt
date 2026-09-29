package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The far end of the loop: a task whose work has left the worktree is closed by the stage its own item reached,
 * so nobody has to come back and press the last button. The condition is the tracker's own word, never a reading
 * of whether the work LOOKS finished.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCloseJob implements Job {

    /** Every task handed over costs a read per poll, so one poll asks about a handful and the rest wait. */
    private static final int READS_PER_POLL = 5;
    /** A task nobody is moving is asked about ever less often, down to twice a day. */
    private static final Duration MAX_WAIT = Duration.ofHours(12);

    private final ConfigService configService;
    private final StateService stateService;
    private final TrackerClose close;
    private final CommandService commands;
    private final Map<String, Deferred> deferred = new ConcurrentHashMap<>();

    /**
     * When a task may be asked about again. {@code statusSince} is what the wait was measured against: a task
     * that has moved since is a different question, and waits no longer.
     */
    private record Deferred(long statusSince, int misses, long nextAt) {
    }

    @Override
    public String id() {
        return "ticket-close";
    }

    @Override
    public String describe() {
        return "close every task whose item reached the stage that says the work landed";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(configService.load().tracker().everyMinutesOrDefault());
    }

    @Override
    public void run() {
        TrackerConfig tracker = configService.load().tracker();
        if (!tracker.modeOrOff().closes() || !tracker.missing().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Duration every = Duration.ofMinutes(tracker.everyMinutesOrDefault());
        int asked = 0;
        for (Map.Entry<String, TaskState> entry : handedOver().entrySet()) {
            if (asked == READS_PER_POLL) {
                return;
            }
            if (!due(entry.getKey(), entry.getValue(), now)) {
                continue;
            }
            asked++;
            ask(entry.getKey(), entry.getValue(), now, every);
        }
    }

    /**
     * A tracker nobody is moving would otherwise be read every poll for as long as the task stands there, which
     * is the one cost here that grows with nothing happening.
     */
    private boolean due(String taskId, TaskState task, long now) {
        Deferred waiting = deferred.get(taskId);
        return waiting == null || waiting.statusSince() != task.statusSince() || now >= waiting.nextAt();
    }

    private void ask(String taskId, TaskState task, long now, Duration every) {
        if (closeIfLanded(taskId, task)) {
            deferred.remove(taskId);
            return;
        }
        Deferred waiting = deferred.get(taskId);
        int misses = waiting != null && waiting.statusSince() == task.statusSince() ? waiting.misses() + 1 : 0;
        long wait = Math.min(MAX_WAIT.toMillis(), every.toMillis() * (1L << Math.min(misses, 20)));
        deferred.put(taskId, new Deferred(task.statusSince(), misses, now + wait));
    }

    private Map<String, TaskState> handedOver() {
        Map<String, TaskState> waiting = new LinkedHashMap<>();
        stateService.tasks().forEach((taskId, task) -> {
            if (FlowRules.handedOver(task.status()) && TaskName.isTicketKey(taskId)) {
                waiting.put(taskId, task);
            }
        });
        return waiting;
    }

    private boolean closeIfLanded(String taskId, TaskState task) {
        if (!close.closes(taskId, task)) {
            return false;
        }
        log.atInfo().setMessage("tracker closed a task")
                .addKeyValue("task", taskId)
                .log();
        // Through the same door a human's press uses, stamped as nobody's judgement but the tracker's.
        OriginContext.as(ActionOrigin.TRACKER, () -> commands.execute(taskId, TaskAction.DONE));
        return true;
    }
}
