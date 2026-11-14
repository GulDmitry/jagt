package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandOutcome;
import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MutationProgress {

  private static final Set<String> DEFECT_STOPS = Set.of("STALLED", "SHIPS", "CHECKS");

  public record Conflict(boolean relayed, boolean expected, boolean stepComes) {}

  private MutationProgress() {}

  public static Optional<String> lacking(
      Mutation.Kind kind,
      StandOutcome outcome,
      List<RunRecord.Step> steps,
      int misses,
      boolean awaitsTheReviewer,
      Conflict conflict) {
    Optional<String> defect = defect(kind, outcome, steps, misses, conflict);
    return defect.isPresent()
        ? defect
        : unreached(kind, outcome, steps, misses, awaitsTheReviewer, conflict);
  }

  private static Optional<String> defect(
      Mutation.Kind kind,
      StandOutcome outcome,
      List<RunRecord.Step> steps,
      int misses,
      Conflict conflict) {
    boolean attack = kind == Mutation.Kind.ATTACK;
    if (!attack && (misses > 0 || outcome.signals().stopTrigger().equals("MISS"))) {
      return Optional.of(
          "a "
              + kind.name().toLowerCase(java.util.Locale.ROOT)
              + " mutation hit a miss: "
              + outcome.detail());
    }
    if (steps.stream().noneMatch(step -> step.origin().equals(ActionOrigin.MCP.name()))) {
      return Optional.of("the session never reported");
    }
    boolean conflicted =
        steps.stream().anyMatch(step -> step.status() == TaskStatus.DEPLOY_CONFLICT);
    if (conflicted && !conflict.expected()) {
      return Optional.of("the run hit a deploy conflict no mutation of it expected");
    }
    if (DEFECT_STOPS.contains(outcome.signals().stopTrigger())) {
      return Optional.of("jagt stopped the task on its own defect: " + outcome.detail());
    }
    if (outcome.signals().unfired() > 0 && !outcome.asked()) {
      return Optional.of("the mutation's event never fired and the session never asked");
    }
    return Optional.empty();
  }

  private static Optional<String> unreached(
      Mutation.Kind kind,
      StandOutcome outcome,
      List<RunRecord.Step> steps,
      int misses,
      boolean awaitsTheReviewer,
      Conflict conflict) {
    boolean attack = kind == Mutation.Kind.ATTACK;
    boolean conflicted =
        steps.stream().anyMatch(step -> step.status() == TaskStatus.DEPLOY_CONFLICT);
    boolean deployed = outcome.status().equals(TaskStatus.DEPLOYED.name());
    boolean reviewed =
        outcome.ending() == StandOutcome.Ending.WAITING_ON_THE_REVIEWER
            || (outcome.withTheCodeHost()
                && awaitsTheReviewer
                && steps.stream().anyMatch(step -> step.status() == TaskStatus.CI_POLLING));
    return deployed
            || (conflicted && (conflict.relayed() || !conflict.stepComes()))
            || outcome.asked()
            || (attack && misses > 0)
            || reviewed
        ? Optional.empty()
        : Optional.of(
            "the run reached neither a deploy, a question, a recorded refusal nor a waiting"
                + " reviewer");
  }
}
