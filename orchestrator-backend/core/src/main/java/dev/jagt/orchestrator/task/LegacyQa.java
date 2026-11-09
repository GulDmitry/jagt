package dev.jagt.orchestrator.task;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads a state file written when the qa role's passed tips, round digest and rows were marks on
 * the turn, into the acceptance record, once, and the step `qa-round` as `acceptance-round`.
 */
final class LegacyQa {

  private static final String PASSED = "qa";
  private static final String DIGEST = "qa:digest";
  private static final String ROWS = "qa:rows";
  private static final String STEP = "qa-round";

  private LegacyQa() {}

  static @Nullable RoleRecord of(
      @Nullable RoleRecord stored, @Nullable Turn turn, TaskStatus status) {
    if (stored != null || turn == null) {
      return stored;
    }
    RoleRecord read =
        new RoleRecord(
            reading(turn, status), mark(turn, PASSED), mark(turn, DIGEST), mark(turn, ROWS));
    return read.isEmpty() ? null : read;
  }

  static @Nullable Turn withoutMarks(@Nullable Turn turn) {
    if (turn == null) {
      return turn;
    }
    String step = STEP.equals(turn.step()) ? Step.ACCEPTANCE_ROUND.id() : turn.step();
    if (step.equals(turn.step())
        && !turn.marks().containsKey(PASSED)
        && !turn.marks().containsKey(DIGEST)
        && !turn.marks().containsKey(ROWS)) {
      return turn;
    }
    Map<String, String> kept = new HashMap<>(turn.marks());
    kept.keySet().removeAll(java.util.List.of(PASSED, DIGEST, ROWS));
    return new Turn(step, turn.id(), turn.digest(), turn.since(), kept, turn.run());
  }

  private static String mark(Turn turn, String name) {
    String value = turn.mark(name);
    return value == null ? "" : value;
  }

  private static String reading(Turn turn, TaskStatus status) {
    return status == TaskStatus.DEPLOYED && turn.open() && AcceptanceVerdict.isStep(turn.step())
        ? turn.step()
        : "";
  }
}
