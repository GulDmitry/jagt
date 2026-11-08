package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopHold;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.master.MasterStopAnswer;
import dev.jagt.orchestrator.service.master.MasterStopPanel;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * In act, a stop jagt set for a loop it suspects is asked of the Master once; what it does not lift
 * stays the human's.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterStopJob implements TaskJob {

  private final ConfigService configService;
  private final MasterStopPanel panel;
  private final MasterStopMoves moves;
  private final TaskTurns turns;
  private final Rounds rounds;

  @Override
  public String id() {
    return "master-stop";
  }

  @Override
  public Serves serves() {
    return Serves.MASTER;
  }

  @Override
  public String describe() {
    return "ask the Master once about a stop for a suspected loop, and lift it where it says so";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(5);
  }

  @Override
  public Watch watch() {
    return Watch.defaults().withStops(StopKind.LIFT);
  }

  @Override
  public void run(String taskId, TaskState task) {
    ConfigService.ConfigFile file = configService.load();
    ConfigService.ConfigFile.MasterConfig config = file.master();
    if (StopHold.of(task, rounds.of(task, file).master()) != StopHold.MASTER) {
      return;
    }
    TaskStop stop = Objects.requireNonNull(task.stop());
    turns.askedStop(taskId, stop.key());
    Optional<MasterStopAnswer> answer =
        panel.ask(taskId, task, config).filter(said -> decides(task, said, config));
    if (answer.isEmpty()) {
      log.atInfo()
          .setMessage("master leaves the stop")
          .addKeyValue("task", taskId)
          .addKeyValue("trigger", stop.trigger())
          .addKeyValue("effect", "the stop is the human's to lift")
          .log();
      return;
    }
    log.atInfo()
        .setMessage("master lifts the stop")
        .addKeyValue("task", taskId)
        .addKeyValue("trigger", stop.trigger())
        .addKeyValue("verdict", answer.get().kind())
        .addKeyValue("reason", answer.get().text())
        .log();
    lift(taskId, answer.get());
  }

  /**
   * A plan waiting is answered by its review, so a task there is not sent back to work; a tier up
   * is the Master's only where {@code mine} does not keep it.
   */
  private static boolean decides(
      TaskState task, MasterStopAnswer answer, ConfigService.ConfigFile.MasterConfig config) {
    return switch (answer.kind()) {
      case LIFT -> true;
      case ESCALATE -> !config.profile().keeps(TaskAction.ESCALATE);
      case RETURN -> !FlowRules.holdsPlan(task.status());
      case HUMAN -> false;
    };
  }

  private void lift(String taskId, MasterStopAnswer answer) {
    if (answer.kind() == MasterStopAnswer.Kind.ESCALATE) {
      if (!moves.escalate(taskId)) {
        log.atWarn()
            .setMessage("master escalate refused")
            .addKeyValue("task", taskId)
            .addKeyValue("cause", "the move is not offered on this stop, or no case allows it")
            .addKeyValue("effect", "the stop is the human's to lift")
            .log();
      }
      return;
    }
    if (moves.lift(taskId)
        && answer.kind() == MasterStopAnswer.Kind.RETURN
        && !moves.returned(taskId, answer.text())) {
      log.atWarn()
          .setMessage("master return not told")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", "the telling was refused or the task moved on")
          .addKeyValue("effect", "the stop is lifted, the session was not told")
          .log();
    }
  }
}
