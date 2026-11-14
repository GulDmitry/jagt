package dev.jagt.orchestrator.service.turn;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.session.HumanWaits;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.turn.HumanWait;
import dev.jagt.orchestrator.task.turn.Turn.Holder;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps on each task the stretches its turn rested with the human. An open one stays in {@code
 * state.json}, so a restart finds it open and the first sweep closes it only where the turn has
 * left the human since.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HumanWaitRecorder {

  private final StateService stateService;
  private final Rounds rounds;
  private final Clock clock;
  private final Checkers checkers;

  @EventListener
  void sweep(StateWritten written) {
    written
        .tasks()
        .forEach((id, task) -> {
          if (!task.equals(
              HumanWaits.noted(task, holder(task), rounds.judged(task), clock.millis()))) {
            note(id);
          }
        });
  }

  private void note(String id) {
    AtomicReference<Optional<HumanWait>> ended = new AtomicReference<>(Optional.empty());
    stateService.updateTask(
        id,
        task -> {
          TaskState noted =
              HumanWaits.noted(task, holder(task), rounds.judged(task), clock.millis());
          ended.set(HumanWaits.ended(task, noted));
          return noted;
        });
    ended
        .get()
        .ifPresent(wait ->
            log.atInfo()
                .setMessage("human wait closed")
                .addKeyValue("task", id)
                .addKeyValue("why", wait.why())
                .addKeyValue("since", wait.since())
                .addKeyValue("until", wait.until())
                .addKeyValue("millis", wait.until() - wait.since())
                .log());
  }

  private Holder holder(TaskState task) {
    Arbiter.Profile master = rounds.of(task).master();
    return Turns.next(
        task.status(), task.report(), StopHold.of(task, master), checkers.of(task), master);
  }
}
