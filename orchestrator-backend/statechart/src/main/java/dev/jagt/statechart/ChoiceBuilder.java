package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The ordered branches of a choice pseudostate: the first whose guard allows is taken when a
 * transition reaches the choice after its exits, else {@link #otherwise}.
 */
public final class ChoiceBuilder<E, X, F, S extends Enum<S>> {

  private final StatechartBuilder<E, X, F> chart;
  private final Consumer<Choice> register;
  private final List<Choice.Branch> branches = new ArrayList<>();

  ChoiceBuilder(StatechartBuilder<E, X, F> chart, Consumer<Choice> register) {
    this.chart = chart;
    this.register = register;
  }

  public ChoiceBuilder<E, X, F, S> branch(Predicate<Active> guard, S target) {
    branches.add(new Choice.Branch(guard, target));
    register.accept(new Choice(List.copyOf(branches), Optional.empty()));
    return this;
  }

  public StatechartBuilder<E, X, F> otherwise(S target) {
    register.accept(new Choice(List.copyOf(branches), Optional.of(target)));
    return chart;
  }
}
