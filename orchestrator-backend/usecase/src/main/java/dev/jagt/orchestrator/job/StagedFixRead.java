package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.ConflictEnding;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Where the Master acts, a deploy conflict is the session's to resolve and the deploy jagt's to
 * finish: the human already pressed it, so the resolution staged in full is the trigger, not a
 * second press.
 */
@Component
@RequiredArgsConstructor
class StagedFixRead implements ConflictRead {

  private final ConfigService configService;
  private final DeployConflicts conflicts;
  private final ConflictHandOff handOff;

  @Override
  public ConflictEnding ending() {
    return ConflictEnding.STAGED_FIX;
  }

  @Override
  public boolean read(String taskId, TaskState task) {
    if (configService.load().master().modeOrDefault() != MasterMode.ACT) {
      return false;
    }
    Optional<DeployConflicts.WaitingConflict> waiting = conflicts.waiting(taskId, task);
    waiting.ifPresent(conflict -> handOff.handle(taskId, conflict));
    return waiting.isPresent();
  }
}
