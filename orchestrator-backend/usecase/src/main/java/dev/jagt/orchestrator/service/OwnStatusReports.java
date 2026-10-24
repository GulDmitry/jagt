package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.Reported;
import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.task.TaskState;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * A session reporting on its own task, whose hand-back must leave the notes the next session starts from, and whose
 * report must be a reply its open turn takes.
 */
@Service
@RequiredArgsConstructor
public class OwnStatusReports {

    private final StateService stateService;
    private final HandBackDue due;
    private final AgentStatusReports statusReports;
    private final CaseDoor door;
    private final TaskTurns turns;

    public String report(AgentStatusMessage said, String taskId) {
        Optional<Reported> accepted = said.accepted(statusReports.contextFor(taskId));
        Optional<TaskState> handedBack = accepted.map(Reported::status)
                .filter(status -> FlowRules.readByTheMaster(status) && !FlowRules.holdsAPlan(status))
                .flatMap(status -> stateService.task(taskId));
        handedBack.flatMap(due::invalid).ifPresent(invalid -> {
            throw new IllegalArgumentException(invalid);
        });
        handedBack.flatMap(task -> due.owed(taskId, task)).ifPresent(owed -> {
            throw Refusal.byState(owed);
        });
        accepted.filter(reply -> !door.takes(taskId, reply.status(), reply.claimed())).ifPresent(reply -> {
            // Closed, so the session's next report is not refused for the same step again.
            turns.refused(taskId);
            throw Refusal.byState("jagt refuses this report: no case takes it as a reply to the step you were sent,"
                    + " so it was not applied and the task is stopped for the human. End your turn.");
        });
        return statusReports.report(said, taskId);
    }
}
