package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.reach.ScenarioReach;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.assistant.TicketTexts;
import dev.jagt.orchestrator.service.scenario.ScenarioSessions;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.acceptance.AcceptanceQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScenariosCapability implements TaskCapability {

  private final ScenarioSessions sessions;
  private final TicketTexts tickets;
  private final StateService state;
  private final ScenarioReach reach;

  @Override
  public TaskAction action() {
    return TaskAction.SCENARIOS;
  }

  @Override
  public boolean offered() {
    return reach.ready();
  }

  @Override
  public Outcome run(String taskId) {
    TaskState task = state.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    String project = task.project();
    if (project == null) {
      throw Refusal.byState(taskId + " has no project: acceptance sessions are one per project");
    }
    return Outcome.ok(sessions.open(project, loaded(taskId, task)));
  }

  private String loaded(String taskId, TaskState task) {
    String ticket =
        tickets
            .of(taskId)
            .map(text -> "<ticket>\n" + text + "\n</ticket>")
            .orElse("The ticket text of " + taskId + " is not read yet.");
    String asked = AcceptanceQuestion.standing(task);
    return "Task " + taskId + ".\n" + ticket + (asked == null ? "" : "\nOpen question:\n" + asked);
  }
}
