package dev.jagt.statechart;

import java.util.List;

public final class ChartInvalid extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<String> problems;

  ChartInvalid(List<String> problems) {
    super("chart is invalid: " + String.join("; ", problems));
    this.problems = List.copyOf(problems);
  }

  public List<String> problems() {
    return problems;
  }
}
