package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskAliasFeed {

  private final StateService stateService;

  @EventListener(ApplicationReadyEvent.class)
  void rememberStored() {
    remember(stateService.tasks());
  }

  @EventListener
  void rememberWritten(StateWritten written) {
    remember(written.tasks());
  }

  private static void remember(Map<String, TaskState> tasks) {
    tasks.forEach((id, task) -> {
      if (task.alias() != null) {
        TaskAliasLogFilter.remember(id, task.alias());
      }
    });
  }
}
