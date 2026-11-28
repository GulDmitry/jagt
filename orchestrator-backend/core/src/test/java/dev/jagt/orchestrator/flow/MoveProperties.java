package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.view.RoundState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.EnumSet;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MoveProperties {

  private static final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  @Property(tries = 5_000)
  void primaryVerbIsAmongTheAllowedVerbsOfItsStatus(
      @ForAll TaskStatus status,
      @ForAll("facts") Facts facts,
      @ForAll("rounds") RoundState round,
      @ForAll boolean agentSilent,
      @ForAll("watches") AutoReviewWatch watch) {
    // planGateStandsShutOnlyWhereNoRoundIsReportedYet (TaskChartProperties)
    Facts reachable =
        facts
            .withDelivery(
                facts
                    .delivery()
                    .withRevertConflict(
                        facts.delivery().revertConflict() && status == TaskStatus.DEPLOY_CONFLICT))
            .withHold(
                facts
                    .hold()
                    .withPlanClosed(
                        facts.hold().planClosed()
                            && EnumSet.of(
                                    TaskStatus.NEW, TaskStatus.PLAN_PENDING, TaskStatus.IN_PROGRESS)
                                .contains(status)));

    Move move = Move.forTask(rules, status, reachable, round, agentSilent, watch);

    assertThat(move.actions()).containsAll(Optional.ofNullable(move.primary()).stream().toList());
  }

  @Provide
  Arbitrary<Facts> facts() {
    Arbitrary<Boolean> flag = Arbitraries.of(true, false);
    Arbitrary<Facts.Delivery> delivery =
        Combinators.combine(flag, flag, flag, flag, flag, flag)
            .as((request, merged, live, conflict, shipped, green) ->
                new Facts.Delivery(
                    request, request && merged, live, conflict, shipped, green, () -> false));
    Arbitrary<Facts.Hold> hold =
        Combinators.combine(flag, flag, flag, flag, flag)
            .as((stopped, paused, arbiters, above, closed) ->
                new Facts.Hold(
                    stopped || paused || arbiters, paused, arbiters, above, closed, () -> false));
    Arbitrary<Facts.Workspace> workspace =
        Combinators.combine(flag, flag, flag)
            .as((changed, requested, several) ->
                new Facts.Workspace(changed, requested, several, Optional::empty, Optional::empty));
    Arbitrary<Facts.Reach> reach =
        Combinators.combine(flag, flag, flag)
            .as((scenarios, shell, approval) -> new Facts.Reach(scenarios, shell, approval, null));
    return Combinators.combine(delivery, hold, workspace, reach).as(Facts::new);
  }

  @Provide
  Arbitrary<RoundState> rounds() {
    Arbitrary<Boolean> flag = Arbitraries.of(true, false);
    return Combinators.combine(Arbitraries.of(AgentReport.class), flag, flag)
        .as((report, replies, reading) ->
            RoundState.of(report, replies).withArbiterReading(reading));
  }

  @Provide
  Arbitrary<AutoReviewWatch> watches() {
    return Arbitraries.of(
        AutoReviewWatch.none(),
        AutoReviewWatch.watching(1),
        AutoReviewWatch.windowElapsed(1),
        AutoReviewWatch.offForTask(),
        AutoReviewWatch.noRound());
  }
}
