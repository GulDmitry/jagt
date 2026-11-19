package dev.jagt.statechart;

import java.util.List;

public final class RaiseCeilingExceeded extends RuntimeException {

  private static final long serialVersionUID = 1L;

  RaiseCeilingExceeded(int ceiling, List<String> moves) {
    super(
        "more than "
            + ceiling
            + " internal events in one step, the cycle runs through: "
            + String.join(", ", moves.subList(Math.max(0, moves.size() - 6), moves.size())));
  }
}
