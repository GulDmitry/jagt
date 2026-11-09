package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.ScenarioSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TicketTexts;
import dev.jagt.orchestrator.task.AcceptanceQuestion;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScenariosCapability implements TaskCapability {

  private final ScenarioSessions sessions;
  private final TicketTexts tickets;
  private final StateService state;

  @Override
  public TaskAction action() {
    return TaskAction.SCENARIOS;
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
