package dev.jagt.statechart;

/**
 * A compound state whose final child was entered, a parallel state whose every region is final, or
 * the chart whose every top-level region is final.
 */
public record Completed(String region, String state) {

  static Completed ofChart() {
    return new Completed("", "");
  }

  /** Whether every region of the chart is final, rather than one compound state. */
  public boolean wholeChart() {
    return region.isEmpty();
  }
}
