package dev.jagt.orchestrator.job.session;

import dev.jagt.orchestrator.flow.capability.Watch;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.turn.Turn;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * A nudge whose Enter was lost to a turn still ending stays in the input for hours; the idle
 * session is pressed again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NudgeResubmitJob implements TaskJob {

  static final String MARK = "nudge-resubmits";
  static final int CEILING = 5;

  private final AgentSessions sessions;
  private final TaskTurns turns;

  @Override
  public String id() {
    return "nudge-resubmit";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "press Enter again on jagt's own nudge left unsent in an idle session's input";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(4);
  }

  @Override
  public Watch watch() {
    return Watch.defaults().withTurns(TurnKind.OPEN);
  }

  @Override
  public void run(String taskId, TaskState task) {
    Turn turn = task.work().turnOrNone();
    int pressed = pressed(turn);
    if (pressed >= CEILING || !sessions.resubmitUnsentNudge(taskId)) {
      return;
    }
    turns.mark(taskId, MARK, turn.id() + ":" + (pressed + 1));
    log.atInfo()
        .setMessage("nudge resubmitted")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("turn", turn.id())
        .addKeyValue("pressed", pressed + 1)
        .addKeyValue("cause", "it sat unsent in an idle input")
        .log();
  }

  private static int pressed(Turn turn) {
    String kept = turn.mark(MARK);
    String prefix = turn.id() + ":";
    return kept != null && kept.startsWith(prefix)
        ? Integer.parseInt(kept.substring(prefix.length()))
        : 0;
  }
}
