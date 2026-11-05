package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.PlanApproval;
import dev.jagt.orchestrator.service.Unplaceable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanCapability implements TaskCapability {

    private final PlanApproval approval;

    @Override
    public TaskAction action() {
        return TaskAction.APPROVE_PLAN;
    }

    @Override
    public Outcome run(String taskId) {
        try {
            return approval.approve(taskId, "The human read plan.md")
                    ? Outcome.relayed("approve-plan " + taskId + ": the session was told the plan holds",
                            "plan approved")
                    : Outcome.nothing("approve-plan " + taskId + ": the session was already told");
        } catch (Unplaceable unplaceable) {
            return Outcome.nothing("approve-plan " + taskId + ": " + unplaceable.question(),
                    "add " + String.join(", ", unplaceable.projects()) + " in the add-project form, then approve").asking();
        }
    }
}
