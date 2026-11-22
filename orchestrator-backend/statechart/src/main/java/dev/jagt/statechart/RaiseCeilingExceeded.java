package dev.jagt.statechart;

import java.util.List;
import java.util.stream.Collectors;

public final class RaiseCeilingExceeded extends RuntimeException {

  private static final long serialVersionUID = 1L;

  RaiseCeilingExceeded(int ceiling, List<Fired> fired) {
    super(
        "more than "
            + ceiling
            + " microsteps after the first in one step, the cycle runs through: "
            + fired.subList(Math.max(0, fired.size() - 6), fired.size()).stream()
                .map(Fired::describe)
                .collect(Collectors.joining(", ")));
  }
}
