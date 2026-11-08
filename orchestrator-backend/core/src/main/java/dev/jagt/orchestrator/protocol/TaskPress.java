package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.flow.TaskAction;
import java.util.Arrays;
import java.util.List;

/** One verb of the board pressed on one task, with the project some verbs take. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskPress(String taskId, String verb, String project) implements Message {

  private static final List<String> VERBS =
      Arrays.stream(TaskAction.values()).map(TaskAction::id).toList();

  public static Schema schema(String description) {
    return Schema.of(description)
        .required("taskId", "string", "Task id or its short alias (p1, s2, …).")
        .choiceRequired("verb", VERBS, null)
        .text("project", "The project an `add-repo` or `remove-repo` names; otherwise left out.");
  }

  public TaskPress {
    taskId = taskId == null || taskId.isBlank() ? null : taskId;
    verb = verb == null || verb.isBlank() ? null : verb.strip();
    project = project == null || project.isBlank() ? null : project.strip();
  }

  @Override
  public List<Violation> violations(MessageContext context) {
    if (taskId == null) {
      return List.of(new Violation("taskId", "required: the task to act on"));
    }
    return verb == null || TaskAction.byId(verb).isEmpty()
        ? List.of(new Violation("verb", "one of " + VERBS))
        : List.of();
  }
}
