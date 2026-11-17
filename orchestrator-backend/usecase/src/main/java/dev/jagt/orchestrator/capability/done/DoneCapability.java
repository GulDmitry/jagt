package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.service.task.ArchivedTaskFolder;
import dev.jagt.orchestrator.service.task.FinishedTasks;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DoneCapability implements TaskCapability {

  private final TaskRetirement retirement;
  private final FinishedTasks finished;
  private final FinishedArtifacts artifacts;
  private final ArchivedTaskFolder folders;

  @Override
  public TaskAction action() {
    return TaskAction.DONE;
  }

  @Override
  public Outcome run(String taskId) {
    // Before retirement: it drops the state entry, and the record is built from it.
    Optional<Path> snapshot = finished.record(taskId).flatMap(artifacts::keep);
    String retired = retirement.retire(taskId);
    folders.shelve(taskId, snapshot.orElse(null));
    return Outcome.gone(retired);
  }
}
