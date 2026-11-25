package dev.jagt.orchestrator.service.review;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.view.RoundState;
import dev.jagt.orchestrator.port.codehost.RequestlessShip;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.reach.SessionHold;
import dev.jagt.orchestrator.service.stop.PhaseProfiles;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a task's last round left behind. */
@Component
@RequiredArgsConstructor
public class Rounds implements PhaseProfiles {

  private final ConfigService configService;
  private final RequestlessShip requestless;
  private final ReachOffers offers;
  private final SessionHold hold;
  private final Checkers checkers;

  public RoundState of(TaskState task) {
    return of(task, configService.load());
  }

  /**
   * Where the Master read the round in front or left the task to the human, nothing of it stands in
   * any more.
   */
  public RoundState of(TaskState task, ConfigFile config) {
    Arbiter.Profile arbiter = config.arbiter().profile().at(task);
    boolean drafted = ReviewDrafts.pending(task, task.standing().status(), config.codeReview());
    return RoundState.of(task.standing().report(), drafted)
        .withRepliesWrittenAt(drafted ? ReviewDrafts.writtenAt(task) : 0)
        .withArbiter(arbiter)
        .withArbiterReading(
            FlowRules.readByTheArbiter(task.standing().status()) && arbiter.running())
        .withCheckers(checkers.of(task))
        .withRequestless(requestless.stands(task))
        .withReach(offers.facts().withSessionHeld(hold.held(task)));
  }

  @Override
  public Arbiter.Profile at(TaskState task) {
    return of(task).arbiter();
  }

  /** The Master runs and has read the plan or round in front. */
  public boolean judged(TaskState task) {
    return configService.load().arbiter().profile().reading(task);
  }
}
