package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Pressed;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * What every surface calls to act on one task. It adds no rules of its own: the gate, the doer and
 * the transition all live in {@link FlowEngine}, where the arbiter may press in {@link CaseDoor}.
 */
@Service
@RequiredArgsConstructor
public class CommandService {

  private final FlowEngine flow;
  private final CaseDoor door;

  /** Runs {@code action} on {@code taskIdOrAlias} and returns the sentence to show the human. */
  public String execute(String taskIdOrAlias, TaskAction action) {
    return execute(taskIdOrAlias, action, null);
  }

  /** As above, with the project a human typed after the ticket, or null. */
  public String execute(String taskIdOrAlias, TaskAction action, @Nullable String project) {
    return press(taskIdOrAlias, action, project).message();
  }

  /**
   * As {@link #execute(String, TaskAction, String)}, with how the work ended beside its sentence.
   */
  public Pressed press(String taskIdOrAlias, TaskAction action, @Nullable String project) {
    return door.presses(taskIdOrAlias, action)
        ? flow.press(taskIdOrAlias, action, project)
        : new Pressed(declined(taskIdOrAlias, action), Outcome.Kind.NOTHING);
  }

  /** As {@link #execute(String, TaskAction)}, with the line a verb that takes one types. */
  public String executeLine(String taskIdOrAlias, TaskAction action, String line) {
    return door.presses(taskIdOrAlias, action)
        ? flow.runLine(taskIdOrAlias, action, line)
        : declined(taskIdOrAlias, action);
  }

  private static String declined(String taskIdOrAlias, TaskAction action) {
    return action.id()
        + " "
        + taskIdOrAlias
        + ": no arbiter case allows it at this status; see misses.jsonl";
  }
}
