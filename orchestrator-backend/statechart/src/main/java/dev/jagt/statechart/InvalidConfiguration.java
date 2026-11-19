package dev.jagt.statechart;

import java.util.List;

public final class InvalidConfiguration extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<String> problems;

  InvalidConfiguration(List<String> problems) {
    super("configuration does not fit the chart: " + String.join("; ", problems));
    this.problems = List.copyOf(problems);
  }

  public List<String> problems() {
    return problems;
  }
}
