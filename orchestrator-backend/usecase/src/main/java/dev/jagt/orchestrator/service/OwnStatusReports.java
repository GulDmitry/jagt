package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.Reported;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A session reporting on its own task, whose hand-back must leave the notes the next session starts from. */
@Service
@RequiredArgsConstructor
public class OwnStatusReports {

    private final StateService stateService;
    private final HandBackDue due;
    private final AgentStatusReports statusReports;

    public String report(AgentStatusMessage said, String taskId) {
        said.accepted(statusReports.contextFor(taskId)).map(Reported::status)
                .filter(status -> FlowRules.readByTheMaster(status) && !FlowRules.holdsAPlan(status))
                .flatMap(status -> stateService.task(taskId)).flatMap(task -> due.owed(taskId, task))
                .ifPresent(owed -> {
                    throw Refusal.byState(owed);
                });
        return statusReports.report(said, taskId);
    }
}
