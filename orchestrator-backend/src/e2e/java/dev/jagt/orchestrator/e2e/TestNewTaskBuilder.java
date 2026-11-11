package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.task.NewTask;
import java.util.List;
import lombok.With;

@With
public record TestNewTaskBuilder(
    String task, List<String> projects, String instructions, String title) {

  public static TestNewTaskBuilder newTask() {
    return new TestNewTaskBuilder(
        "ABC-7", List.of("proj"), "Fix the widget", "Widget layout is off");
  }

  public NewTask build() {
    return new NewTask(task, projects, instructions, null, null, null, title, null);
  }
}
