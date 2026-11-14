package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public final class LegalMoves {

  private static final List<Facts> EVERY_FACT =
      IntStream.range(0, 64)
          .mapToObj(bits ->
              Facts.defaults()
                  .withDelivery(
                      Facts.Delivery.defaults()
                          .withHasReviewRequest(bit(bits, 0))
                          .withLiveDeploy(bit(bits, 1)))
                  .withHold(
                      Facts.Hold.defaults()
                          .withAgentLive(() -> bit(bits, 2))
                          .withStopped(bit(bits, 4)))
                  .withWorkspace(
                      Facts.Workspace.defaults()
                          .withGitStateChanged(bit(bits, 3))
                          .withRepoRequested(bit(bits, 5))))
          .toList();

  private final FlowRules rules;

  public LegalMoves(FlowRules rules) {
    this.rules = rules;
  }

  public boolean allows(TaskStatus from, TaskStatus to) {
    return from == to || byAction(from, to) || byReport(from, to) || byRead(from, to);
  }

  public boolean reaches(TaskStatus from, TaskStatus to) {
    Set<TaskStatus> seen = EnumSet.of(from);
    Deque<TaskStatus> open = new ArrayDeque<>(seen);
    while (!open.isEmpty()) {
      TaskStatus at = open.pop();
      for (TaskStatus next : TaskStatus.values()) {
        if (allows(at, next) && seen.add(next)) {
          open.push(next);
        }
      }
    }
    return seen.contains(to);
  }

  private boolean byAction(TaskStatus from, TaskStatus to) {
    for (TaskAction action : TaskAction.values()) {
      boolean allowed = EVERY_FACT.stream().anyMatch(facts -> rules.allows(from, action, facts));
      for (Outcome.Kind kind : Outcome.Kind.values()) {
        if (allowed && rules.next(from, action, kind).equals(Optional.of(to))) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean byReport(TaskStatus from, TaskStatus to) {
    for (TaskStatus reported : TaskStatus.values()) {
      boolean accepted = rules.refusedReport(from, reported).isEmpty();
      if (accepted
          && (rules.reported(from, reported, false) == to
              || rules.reported(from, reported, true) == to)) {
        return true;
      }
    }
    return false;
  }

  private boolean byRead(TaskStatus from, TaskStatus to) {
    return FlowRules.reads().stream().anyMatch(read -> rules.readLands(from, read) == to);
  }

  private static boolean bit(int bits, int index) {
    return (bits >> index & 1) == 1;
  }
}
