package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.capability.deploy.GitCaseFacts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.StateService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HandOffCapability implements TaskCapability {

  private final StateService stateService;
  private final DeployTargets deployTargets;
  private final GitCaseFacts facts;
  private final AgentSessions sessions;

  @Override
  public TaskAction action() {
    return TaskAction.HAND_OFF;
  }

  @Override
  public Outcome run(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    var task = stateService.task(id).orElseThrow(() -> Refusal.noSuchTask(taskId));
    List<String> cases = new ArrayList<>();
    for (DeployTargets.Target target : deployTargets.all(task)) {
      List<String> found = facts.cases(target.path(), id, target.project(), target.deployBranch());
      if (!found.isEmpty()) {
        sessions.admit(id, target.path());
      }
      cases.addAll(found);
    }
    if (cases.isEmpty()) {
      throw Refusal.byState("hand-off " + id + ": no repository of it stands in a git case");
    }
    sessions.say(id, id + " stands in a git case, work it: " + String.join(" ", cases));
    return Outcome.relayed(
        "Handed " + cases.size() + " git case(s) of " + id + " to its session.",
        "git case handed to the session");
  }
}
