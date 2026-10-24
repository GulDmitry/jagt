package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** One pending wake per task, kept on the task's turn so it outlives a restart; taken off before it is returned. */
@Component
@Slf4j
@RequiredArgsConstructor
public class SessionWakes {

    private static final String WAKE = "wake-at";

    private final UsageLimits limits;
    private final UsageLimitsProperties properties;
    private final StateService stateService;
    private final Clock clock;

    /** Arms a wake at the reset of every window at its limit; none where no window is, or one carries no reset. */
    public synchronized void arm(String taskId) {
        if (!properties.wakes()) {
            return;
        }
        Optional<Instant> at = limits.refresh(clock.instant()).map(UsageLimits.Reading::answer)
                .filter(SubscriptionUsage.Read.class::isInstance).map(SubscriptionUsage.Read.class::cast)
                .flatMap(read -> resetOfExhausted(read.snapshot().windows()));
        if (at.isEmpty()) {
            log.atInfo().setMessage("session wake not armed").addKeyValue("task", taskId)
                    .addKeyValue("cause", "no window at its limit with a known reset").log();
            return;
        }
        mark(taskId, turn -> turn.withMark(WAKE, String.valueOf(at.get().toEpochMilli())));
        log.atInfo().setMessage("session wake armed").addKeyValue("task", taskId)
                .addKeyValue("at", at.get()).log();
    }

    public synchronized void cancel(String taskId) {
        if (stateService.task(taskId).map(task -> task.turnOrNone().mark(WAKE)).isPresent()) {
            mark(taskId, turn -> turn.withoutMark(WAKE));
            log.atInfo().setMessage("session wake cancelled").addKeyValue("task", taskId).log();
        }
    }

    /** Due wakes not held back by {@code held}; a held one stays pending for the next call. */
    public synchronized List<String> takeDue(Predicate<String> held) {
        long now = clock.millis();
        List<String> due = stateService.tasks().entrySet().stream()
                .filter(task -> wakeAt(task.getValue()) <= now)
                .map(Map.Entry::getKey).filter(taskId -> !held.test(taskId)).sorted().toList();
        due.forEach(taskId -> mark(taskId, turn -> turn.withoutMark(WAKE)));
        return due;
    }

    private static long wakeAt(TaskState task) {
        String at = task.turnOrNone().mark(WAKE);
        return at == null ? Long.MAX_VALUE : Long.parseLong(at);
    }

    private void mark(String taskId, UnaryOperator<Turn> change) {
        stateService.updateTask(taskId, task -> task.withTurn(change.apply(task.turnOrNone())));
    }

    private static Optional<Instant> resetOfExhausted(List<SubscriptionUsage.Window> windows) {
        List<SubscriptionUsage.Window> exhausted = windows.stream().filter(window -> window.percentUsed() >= 100)
                .toList();
        if (exhausted.isEmpty() || exhausted.stream().anyMatch(window -> window.resetsAt().isEmpty())) {
            return Optional.empty();
        }
        return exhausted.stream().map(window -> window.resetsAt().get()).max(Comparator.naturalOrder());
    }
}
