package dev.jagt.statechart;

/**
 * A compound state whose final child was entered, a parallel state whose every region is final, or
 * the chart whose every top-level region is final.
 */
public sealed interface Completed {

  /** The chart whose every top-level region is final. */
  record WholeChart() implements Completed {}

  /** A compound or parallel state of {@code region} that completed. */
  record OfState(String region, String state) implements Completed {}
}
