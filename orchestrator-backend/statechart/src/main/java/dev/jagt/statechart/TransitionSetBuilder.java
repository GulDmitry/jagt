package dev.jagt.statechart;

import java.util.List;

public final class TransitionSetBuilder<E, X, F, S extends Enum<S>> {

  private final StatechartBuilder<E, X, F> chart;
  private final List<TransitionBuilder<E, X, F, S>> transitions;

  TransitionSetBuilder(
      StatechartBuilder<E, X, F> chart, List<TransitionBuilder<E, X, F, S>> transitions) {
    this.chart = chart;
    this.transitions = transitions;
  }

  public TransitionSetBuilder<E, X, F, S> emit(F effect) {
    transitions.forEach(transition -> transition.emit(effect));
    return this;
  }

  public TransitionSetBuilder<E, X, F, S> when(Guard<E> allowed) {
    transitions.forEach(transition -> transition.when(allowed));
    return this;
  }

  /** Every source keeps its state, exiting and entering nothing. */
  public StatechartBuilder<E, X, F> internal() {
    transitions.forEach(TransitionBuilder::internal);
    return chart;
  }

  /** Every source leads to {@code target}; the one standing there stays. */
  public StatechartBuilder<E, X, F> to(S target) {
    transitions.forEach(transition -> transition.leadsTo(target));
    return chart;
  }
}
