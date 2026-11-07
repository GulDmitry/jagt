package dev.jagt.orchestrator.task;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads a state file written when QA's passed tips, round digest and rows were marks on the turn,
 * into the QA record, once.
 */
final class LegacyQa {

  private static final String PASSED = "qa";
  private static final String DIGEST = "qa:digest";
  private static final String ROWS = "qa:rows";

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
    if (turn == null
        || (!turn.marks().containsKey(PASSED)
            && !turn.marks().containsKey(DIGEST)
            && !turn.marks().containsKey(ROWS))) {
      return turn;
    }
    Map<String, String> kept = new HashMap<>(turn.marks());
    kept.keySet().removeAll(java.util.List.of(PASSED, DIGEST, ROWS));
    return new Turn(turn.step(), turn.id(), turn.digest(), turn.since(), kept, turn.run());
  }

  private static String mark(Turn turn, String name) {
    String value = turn.mark(name);
    return value == null ? "" : value;
  }

  private static String reading(Turn turn, TaskStatus status) {
    return status == TaskStatus.DEPLOYED && turn.open() && QaVerdict.isStep(turn.step())
        ? turn.step()
        : "";
  }
}
