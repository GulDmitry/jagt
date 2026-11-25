package dev.jagt.orchestrator.service.project;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.StateService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectRemoval {

  private final ProjectSetup setup;
  private final StateService state;

  /** Deletes the project, unless a task not yet done works in it. */
  public String remove(String key) {
    List<String> using =
        state.tasks().entrySet().stream()
            .filter(task ->
                !FlowRules.finished(task.getValue().standing().status())
                    && task.getValue().projects().contains(key))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    if (!using.isEmpty()) {
      throw new IllegalArgumentException(
          "The project "
              + key
              + " is used by "
              + String.join(", ", using)
              + ": finish "
              + (using.size() == 1 ? "that task" : "those tasks")
              + " first");
    }
    return setup.remove(key);
  }
}
