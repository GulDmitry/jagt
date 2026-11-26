package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What the statechart's data are read from: the record's own facts that no region holds, and who
 * pressed. A projection presses nothing and counts as human.
 */
record ChartFacts(
    boolean stampedAsk,
    Map<String, CheckerPhase> beneath,
    Facts facts,
    @Nullable ActionOrigin origin) {

  static final ActionOrigin PROJECTED = ActionOrigin.BOARD;

  private static final Set<ActionOrigin> HUMAN = Set.of(ActionOrigin.BOARD, ActionOrigin.PALETTE);

  CheckerPhase beneath(String checker) {
    return beneath.getOrDefault(checker, CheckerPhase.NONE);
  }

  boolean human() {
    return human(origin);
  }

  static boolean human(@Nullable ActionOrigin by) {
    return by != null && HUMAN.contains(by);
  }
}
