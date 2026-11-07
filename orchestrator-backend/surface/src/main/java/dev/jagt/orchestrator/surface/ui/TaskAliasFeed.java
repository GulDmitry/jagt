package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskAliasFeed {

  private final StateService stateService;

  @PostConstruct
  void follow() {
    remember(stateService.tasks());
    stateService.onChange(written -> remember(written.tasks()));
  }

  private static void remember(Map<String, TaskState> tasks) {
    tasks.forEach(
        (id, task) -> {
          if (task.alias() != null) {
            TaskAliasLogFilter.remember(id, task.alias());
          }
        });
  }
}
