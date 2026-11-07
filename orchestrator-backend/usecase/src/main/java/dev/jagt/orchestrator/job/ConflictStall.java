package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** A handed deploy conflict whose worktree stops moving goes to the Master where it answers, then to the human: each once, kept in the task's state. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictStall {

    private final StateService stateService;
    private final Notifications notifications;
    private final ConfigService config;
    private final Clock clock;

    boolean handed(String taskId) {
        return stateService.task(taskId).flatMap(TaskState::currentConflict).filter(Conflict::handed).isPresent();
    }

    void watch(String taskId, DeployConflicts.WaitingConflict conflict) {
        long now = clock.millis();
        Duration after = config.load().conflictStallOrDefault();
        AtomicReference<Conflict.@Nullable Told> told = new AtomicReference<>();
        stateService.updateTask(taskId, task -> {
            TaskState seen = TaskReducer.deployStepped(task, new DeployStep.Seen(conflict.progress()), now);
            Optional<Conflict> due = seen.currentConflict().filter(standing -> standing.due(after, now));
            if (due.isEmpty()) {
                return seen;
            }
            String reason = due.get().reason(conflict.worktree().toString());
            if (!due.get().askedTheMaster() && masterAnswers(seen)) {
                return TaskReducer.conflictReferred(seen, "The " + kindOf(seen) + " conflict stalled: " + reason, now);
            }
            TaskState stalled = TaskReducer.deployStepped(seen, new DeployStep.Told(reason), now);
            stalled.currentConflict().map(Conflict::told).ifPresent(told::set);
            return stalled;
        });
        Optional.ofNullable(told.get()).ifPresent(stop -> announce(taskId, stop, after));
    }

    /** The question a session would put here, were it the one asking, goes to the Master: the same fact {@code MasterAnswerJob} answers by. */
    private boolean masterAnswers(TaskState task) {
        return !task.report().asks() && Turns.next(task.status(), AgentReport.QUESTION, task.stopped(),
                Checkers.of(task), config.load().master().profile()).equals(Turn.Holder.ARBITER);
    }

    private static String kindOf(TaskState task) {
        return Facts.revertConflicted(task) ? "revert" : "deploy";
    }

    private void announce(String taskId, Conflict.Told stop, Duration after) {
        Optional<TaskState> task = stateService.task(taskId);
        String alias = task.map(TaskState::alias).orElse(taskId);
        String kind = task.map(ConflictStall::kindOf).orElse("deploy");
        log.atWarn().setMessage(kind + " conflict stalled").addKeyValue("task", taskId).addKeyValue("alias", alias)
                .addKeyValue("cause", "no change in the " + kind + " worktree for " + after.toMinutes() + " min")
                .addKeyValue("said", stop.reason())
                .addKeyValue("effect", "the human is told once; the " + kind + " waits")
                .addKeyValue("fix", "resolve it in the " + kind + " worktree, git add, then " + kind + " " + alias)
                .log();
        notifications.send(Notification.watchdog(taskId, kind + " conflict stalled", stop.reason()));
    }
}
