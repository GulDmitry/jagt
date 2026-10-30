package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The ONE place a task's status is decided and written, whoever asked: no doer names a status, and a refusal is
 * answered with a sentence instead of a git error three layers down.
 */
@RequiredArgsConstructor
public class FlowEngine {

    private final TaskStore tasks;
    private final Capabilities capabilities;
    private final AgentPresence agents;
    private final Clock clock;

    /** Runs {@code action} on the task and returns what to show the human. */
    public String run(String taskIdOrAlias, TaskAction action) {
        String taskId = tasks.canonicalTaskId(taskIdOrAlias);
        TaskState task = tasks.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskIdOrAlias));
        if (!FlowRules.allows(task.status(), action, facts(task, taskId))) {
            throw new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE, action.label() + " is not available for "
                    + taskId + " (it is " + task.status() + " — "
                    + Move.forTask(task.status(), Facts.projected(task),
                            dev.jagt.orchestrator.flow.RoundState.of(task.report(), false),
                            SessionPhase.silent(task)).hint() + ")");
        }
        TaskCapability capability = capabilities.of(action).orElseThrow(() ->
                new IllegalStateException("No capability declared for `" + action.id() + "`"));
        return apply(taskId, task.status(), action, wrapped(taskId, action, capability));
    }


    /** The work, inside whatever an install declared around this verb; the capability itself is innermost. */
    private Outcome wrapped(String taskId, TaskAction action, TaskCapability capability) {
        Supplier<Outcome> work = () -> capability.run(taskId);
        for (CapabilityInterceptor interceptor : capabilities.around(action).reversed()) {
            Supplier<Outcome> inner = work;
            work = () -> interceptor.around(taskId, inner);
        }
        return work.get();
    }

    private String apply(String taskId, TaskStatus was, TaskAction action, Outcome outcome) {
        Optional<TaskStatus> next = FlowRules.next(was, action, outcome.kind());
        if (next.isPresent() || outcome.stamp() != null) {
            TaskMove move = new TaskMove.Ran(action, outcome.kind());
            // Recorded even when the status is unchanged: a second round onto the same request happened.
            tasks.updateTask(taskId, task -> {
                String line = outcome.stamp() == null ? task.message() : outcome.stamp();
                long at = clock.millis();
                TaskState written = next.isPresent() ? TaskReducer.reduce(task, move, line, true, at)
                        : TaskReducer.stayed(task, line, true, at);
                return outcome.stamp() == null ? written : TaskReducer.stamped(written, outcome.stampMeans());
            });
        }
        if (outcome.kind().refuses()) {
            throw new IllegalStateException(outcome.message(), outcome.cause());
        }
        return outcome.message();
    }

    private boolean live(String taskId) {
        try {
            return agents.agentLive(taskId);
        } catch (AgentPresence.Unread unread) {
            throw Refusal.byState("Cannot tell whether the session runs, its window was not read: "
                    + unread.getMessage());
        }
    }

    /** The real liveness probe, unlike the projection's: this one is about to act on the answer. */
    private Facts facts(TaskState task, String taskId) {
        return new Facts(task.hasReviewRequest(), Facts.deployLive(task), () -> live(taskId),
                task.gitStateChanged(), task.stopped(), task.asksForARepository(), task.requestsMerged(),
                Facts.revertConflicted(task));
    }
}
