package dev.jagt.orchestrator.flow;

import java.util.List;

/**
 * A move that met states of the chart with no class declared for its kind: {@code where} names each
 * as {@code region.state}, {@code refused} where the move was a claim or a press.
 */
public record Unclassified(String kind, List<String> where, boolean refused) {

  static Unclassified of(TaskMove move, List<String> where) {
    return new Unclassified(
        move.getClass().getSimpleName(), List.copyOf(where), move instanceof TaskMove.Asked);
  }
}
