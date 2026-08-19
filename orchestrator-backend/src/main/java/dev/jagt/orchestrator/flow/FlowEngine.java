package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.capability.Capabilities;
import dev.jagt.orchestrator.capability.TaskCapability;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.Refusal;
import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The ONE place a task's status is decided and written, whoever asked. It checks the action against
 * {@link FlowRules}, runs the capability, and applies the transition the rules give for that outcome — so no doer
 * names a status, and a refusal is answered with a sentence instead of a git error three layers down.
 */
@Service
@RequiredArgsConstructor
public class FlowEngine {

    private final StateService stateService;
    private final Capabilities capabilities;
    private final AgentSessions sessions;

    /** Runs {@code action} on the task and returns what to show the human. */
    public String run(String taskIdOrAlias, TaskAction action) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        TaskState task = stateService.task(taskId).orElseThrow(() -> new Refusal(Refusal.Code.NO_SUCH_TASK,
                "No task " + taskIdOrAlias + " — it may have been closed since this page loaded."));
        if (!FlowRules.allows(task.status(), action, facts(task, taskId))) {
            throw new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE, action.label() + " is not available for "
                    + taskId + " (it is " + task.status() + " — "
                    + Move.forTask(task.status(), task.hasReviewRequest(),
                            dev.jagt.orchestrator.model.RoundState.of(task.message(), false)).hint() + ")");
        }
        TaskCapability capability = capabilities.of(action).orElseThrow(() ->
                new IllegalStateException("No capability declared for `" + action.id() + "`"));
        return apply(taskId, task.status(), action, capability.run(taskId));
    }

    /**
     * A status the task itself reports — its agent over MCP, or a review round jagt read on its behalf. The same
     * table decides, so a task cannot talk itself onto a shared branch, out of one, or closed.
     */
    public boolean report(String taskId, TaskStatus status, String message) {
        if (!FlowRules.reportable(status)) {
            throw new IllegalArgumentException("Status " + status + " is jagt's to set, not a task's to report");
        }
        return stateService.updateTask(taskId, task -> task.withStatus(status, message));
    }

    private String apply(String taskId, TaskStatus was, TaskAction action, Outcome outcome) {
        Optional<TaskStatus> next = FlowRules.next(action, outcome.kind());
        if (outcome.stamp() != null) {
            TaskStatus stamped = next.orElse(was);
            // Recorded even when the status is unchanged: a second round shipped onto the same request, or a
            // deploy that stopped part way, both happened and both are what a human reads the history for.
            stateService.updateTask(taskId, task -> task.withStatus(stamped, outcome.stamp(), true));
        }
        if (outcome.kind().refuses()) {
            throw new IllegalStateException(outcome.message(), outcome.cause());
        }
        return outcome.message();
    }

    /** The real liveness probe, unlike the projection's: this one is about to act on the answer. */
    private Facts facts(TaskState task, String taskId) {
        return new Facts(task.hasReviewRequest(), () -> sessions.agentLive(taskId));
    }
}
