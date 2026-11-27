package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.turn.TurnEndVerdict;
import dev.jagt.orchestrator.service.turn.TurnEndVerdicts;
import dev.jagt.orchestrator.service.turn.TurnEnding;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** A turn end's verdict, in the words the CLI reads. */
@Service
@RequiredArgsConstructor
public class TurnEnds {

  private final AgentRuntime runtime;
  private final TurnEndVerdicts verdicts;

  public String answer(
      String taskId,
      @Nullable TaskState task,
      boolean sentOn,
      boolean pausedOnBackgroundWork,
      @Nullable String turnText) {
    return switch (verdicts.of(
        taskId, task, new TurnEnding(sentOn, pausedOnBackgroundWork, turnText))) {
      case TurnEndVerdict.Refuse refuse -> runtime.refusedTurnEnd(refuse.reason());
      case TurnEndVerdict.Tell tell -> runtime.toldTheHuman(tell.note());
      case TurnEndVerdict.Silent silent -> "";
    };
  }
}
