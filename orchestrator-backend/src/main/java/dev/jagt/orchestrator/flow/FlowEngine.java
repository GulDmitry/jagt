package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.CapabilityInterceptor;
import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The ONE place a task's status is decided and written, whoever asked. It checks the action against
 * {@link FlowRules}, runs the capability, and applies the transition the rules give for that outcome — so no doer
 * names a status, and a refusal is answered with a sentence instead of a git error three layers down.
 */
public class FlowEngine {

    public FlowEngine(TaskStore tasks, Capabilities capabilities, AgentPresence agents) {
        this.tasks = tasks;
        this.capabilities = capabilities;
        this.agents = agents;
    }

    private final TaskStore tasks;
    private final Capabilities capabilities;
    private final AgentPresence agents;

    /** Runs {@code action} on the task and returns what to show the human. */
    public String run(String taskIdOrAlias, TaskAction action) {
        String taskId = tasks.canonicalTaskId(taskIdOrAlias);
        TaskState task = tasks.task(taskId).orElseThrow(() -> new Refusal(Refusal.Code.NO_SUCH_TASK,
                "No task " + taskIdOrAlias + " — it may have been closed since this page loaded."));
        if (!FlowRules.allows(task.status(), action, facts(task, taskId))) {
            throw new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE, action.label() + " is not available for "
                    + taskId + " (it is " + task.status() + " — "
                    + Move.forTask(task.status(), task.hasReviewRequest(),
                            dev.jagt.orchestrator.flow.RoundState.of(task.message(), false)).hint() + ")");
        }
        TaskCapability capability = capabilities.of(action).orElseThrow(() ->
                new IllegalStateException("No capability declared for `" + action.id() + "`"));
        return apply(taskId, task.status(), action, wrapped(taskId, action, capability));
    }

    /**
     * A status the task itself reports — its agent over MCP, or a review round jagt read on its behalf. The same
     * table decides, so a task cannot talk itself onto a shared branch, out of one, or closed.
     */
    public boolean report(String taskId, TaskStatus status, String message) {
        if (!FlowRules.reportable(status)) {
            throw new IllegalArgumentException("Status " + status + " is jagt's to set, not a task's to report");
        }
        return tasks.updateTask(taskId, task -> task.withStatus(status, message));
    }

    /**
     * The work, inside whatever an install declared around this verb. Innermost is the capability itself, so an
     * interceptor that refuses stops the work AND the transition — nothing has happened to report.
     */
    private Outcome wrapped(String taskId, TaskAction action, TaskCapability capability) {
        Supplier<Outcome> work = () -> capability.run(taskId);
        for (CapabilityInterceptor interceptor : capabilities.around(action).reversed()) {
            Supplier<Outcome> inner = work;
            work = () -> interceptor.around(taskId, inner);
        }
        return work.get();
    }

    private String apply(String taskId, TaskStatus was, TaskAction action, Outcome outcome) {
        Optional<TaskStatus> next = FlowRules.next(action, outcome.kind());
        if (outcome.stamp() != null) {
            TaskStatus stamped = next.orElse(was);
            // Recorded even when the status is unchanged: a second round shipped onto the same request, or a
            // deploy that stopped part way, both happened and both are what a human reads the history for.
            tasks.updateTask(taskId, task -> task.withStatus(stamped, outcome.stamp(), true));
        }
        if (outcome.kind().refuses()) {
            throw new IllegalStateException(outcome.message(), outcome.cause());
        }
        return outcome.message();
    }

    /** The real liveness probe, unlike the projection's: this one is about to act on the answer. */
    private Facts facts(TaskState task, String taskId) {
        return new Facts(task.hasReviewRequest(), () -> agents.agentLive(taskId));
    }
}
