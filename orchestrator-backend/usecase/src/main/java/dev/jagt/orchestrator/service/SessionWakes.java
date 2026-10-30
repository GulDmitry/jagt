package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Wake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * One pending wake per task, kept on the task's turn so it outlives a restart; taken off before it is returned.
 * A wake belongs to the turn it was armed in: once another turn opens, it is never due.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SessionWakes {

    /** A step due to be said to the session of {@code taskId}. */
    public record Due(String taskId, String step, Standing read) {
    }

    private final StateService stateService;
    private final TaskTurns turns;
    private final Clock clock;

    /** Arms the one wake of the task, replacing any pending, for the turn standing now. */
    public synchronized void arm(String taskId, Instant at, String step) {
        long turn = stateService.task(taskId).map(task -> task.turnOrNone().id()).orElse(0L);
        turns.mark(taskId, Wake.MARK, new Wake(turn, at.toEpochMilli(), step).mark());
        log.atInfo().setMessage("session wake armed").addKeyValue("task", taskId)
                .addKeyValue("step", step).addKeyValue("at", at).log();
    }

    public void armIn(String taskId, Duration after, String step) {
        arm(taskId, clock.instant().plus(after), step);
    }

    public synchronized void cancel(String taskId) {
        if (stateService.task(taskId).map(task -> task.turnOrNone().mark(Wake.MARK)).isPresent()) {
            turns.unmark(taskId, Wake.MARK);
            log.atInfo().setMessage("session wake cancelled").addKeyValue("task", taskId).log();
        }
    }

    /** Due wakes not held back by {@code held}, which stays pending for the next call; one of a turn gone by is dropped. */
    public synchronized List<Due> takeDue(Predicate<String> held) {
        long now = clock.millis();
        List<Due> due = new ArrayList<>();
        stateService.tasks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Optional<Wake> wake = wakeOf(entry.getValue()).filter(pending -> pending.at() <= now);
            if (wake.isEmpty() || held.test(entry.getKey())) {
                return;
            }
            turns.unmark(entry.getKey(), Wake.MARK);
            if (wake.get().turn() == entry.getValue().turnOrNone().id()) {
                due.add(new Due(entry.getKey(), wake.get().step(), Standing.of(entry.getValue())));
            }
        });
        return due;
    }

    private static Optional<Wake> wakeOf(TaskState task) {
        return Wake.parse(task.turnOrNone().mark(Wake.MARK));
    }
}
