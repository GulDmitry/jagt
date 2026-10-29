package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** One pending wake per task, kept on the task's turn so it outlives a restart; taken off before it is returned. */
@Component
@Slf4j
@RequiredArgsConstructor
public class SessionWakes {

    private static final String WAKE = "wake-at";

    private final StateService stateService;
    private final TaskTurns turns;
    private final Clock clock;

    /** Arms the one wake of the task, replacing any pending. */
    public synchronized void arm(String taskId, Instant at) {
        turns.mark(taskId, WAKE, String.valueOf(at.toEpochMilli()));
        log.atInfo().setMessage("session wake armed").addKeyValue("task", taskId)
                .addKeyValue("at", at).log();
    }

    public synchronized void cancel(String taskId) {
        if (stateService.task(taskId).map(task -> task.turnOrNone().mark(WAKE)).isPresent()) {
            turns.unmark(taskId, WAKE);
            log.atInfo().setMessage("session wake cancelled").addKeyValue("task", taskId).log();
        }
    }

    /** Due wakes not held back by {@code held}; a held one stays pending for the next call. */
    public synchronized List<String> takeDue(Predicate<String> held) {
        long now = clock.millis();
        List<String> due = stateService.tasks().entrySet().stream()
                .filter(task -> wakeAt(task.getValue()) <= now)
                .map(Map.Entry::getKey).filter(taskId -> !held.test(taskId)).sorted().toList();
        due.forEach(taskId -> turns.unmark(taskId, WAKE));
        return due;
    }

    private static long wakeAt(TaskState task) {
        String at = task.turnOrNone().mark(WAKE);
        return at == null ? Long.MAX_VALUE : Long.parseLong(at);
    }
}
