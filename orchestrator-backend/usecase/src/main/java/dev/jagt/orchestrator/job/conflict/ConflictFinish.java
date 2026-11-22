package dev.jagt.orchestrator.job.conflict;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A resolution staged in full finishes the deploy or revert the human pressed, as the Master,
 * unless jagt stopped the task.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictFinish {

  private final CommandService commands;
  private final TaskStops stops;
  private final StateService stateService;
  private final Clock clock;

  void press(String taskId, Path worktree) {
    Optional<TaskStop> stop = stateService.task(taskId).map(taskState -> taskState.work().stop());
    if (stop.isPresent()) {
      hold(taskId, stop.get());
      return;
    }
    if (!stops.moveDue(taskId)) {
      return;
    }
    TaskAction action =
        stateService.task(taskId).filter(Facts::revertConflicted).isPresent()
            ? TaskAction.REVERT
            : TaskAction.DEPLOY;
    try {
      String result = OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, action));
      log.atInfo()
          .setMessage("arbiter conflict finish pressed")
          .addKeyValue("task", taskId)
          .addKeyValue("action", action.id())
          .addKeyValue("worktree", worktree)
          .addKeyValue("result", result)
          .log();
    } catch (Refusal refusal) {
      log.atWarn()
          .setMessage("arbiter conflict finish refused")
          .addKeyValue("task", taskId)
          .addKeyValue("action", action.id())
          .addKeyValue("cause", Causes.of(refusal))
          .addKeyValue("effect", "the conflict waits")
          .log();
    }
  }

  private void hold(String taskId, TaskStop stop) {
    boolean said =
        stateService
            .task(taskId)
            .flatMap(TaskState::currentConflict)
            .filter(conflict -> conflict.isHeldBy(stop.at()))
            .isPresent();
    if (!said) {
      stateService.reduce(
          taskId,
          (reducer, task) ->
              reducer.deployStepped(task, new DeployStep.Held(stop.at()), clock.millis()));
      log.atInfo()
          .setMessage("conflict resolved held by stop")
          .addKeyValue("task", taskId)
          .addKeyValue("trigger", stop.trigger())
          .log();
    }
  }
}
