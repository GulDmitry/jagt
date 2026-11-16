package dev.jagt.orchestrator.capability.code;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodeLauncher {

  private final StateService stateService;
  private final CodeProject project;
  private final CodeDiff diff;

  /**
   * How a task opens: as projects that run, or as a static diff against what its request targets.
   */
  public enum Mode {
    PROJECT,
    DIFF
  }

  public String open(String taskIdOrAlias, Mode mode) {
    String taskId = stateService.canonicalTaskId(taskIdOrAlias);
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    return mode == Mode.DIFF ? diff.open(taskId, task) : project.open(taskId, task);
  }
}
