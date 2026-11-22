package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TransitionBuilder<E, X, F, S extends Enum<S>> {

  private final StatechartBuilder<E, X, F> chart;
  private final String region;
  private final S source;
  private final Transition.Trigger<E> trigger;
  private Transition.Gate<E> gate = (active, event) -> true;
  private final List<Function<Optional<E>, F>> effects = new ArrayList<>();
  private final List<E> raises = new ArrayList<>();
  private int group;

  TransitionBuilder(
      StatechartBuilder<E, X, F> chart, String region, S source, Transition.Trigger<E> trigger) {
    this.chart = chart;
    this.region = region;
    this.source = source;
    this.trigger = trigger;
  }

  TransitionBuilder<E, X, F, S> inGroup(int id) {
    group = id;
    return this;
  }

  /** Not for an eventless transition, which has no event to read: use {@link #whenActive}. */
  public TransitionBuilder<E, X, F, S> when(Guard<E> allowed) {
    requireEvent("when");
    gate = (active, event) -> allowed.allows(active, event.orElseThrow());
    return this;
  }

  /**
   * A guard that reads the configuration and data model alone; the only one an eventless transition
   * has.
   */
  public TransitionBuilder<E, X, F, S> whenActive(Predicate<Active> allowed) {
    gate = (active, event) -> allowed.test(active);
    return this;
  }

  public TransitionBuilder<E, X, F, S> emit(F effect) {
    effects.add(event -> effect);
    return this;
  }

  public TransitionBuilder<E, X, F, S> emitFor(Function<E, F> effect) {
    requireEvent("emitFor");
    effects.add(event -> effect.apply(event.orElseThrow()));
    return this;
  }

  public TransitionBuilder<E, X, F, S> raise(E event) {
    raises.add(event);
    return this;
  }

  public StatechartBuilder<E, X, F> to(S target) {
    chart.add(
        new Transition<>(
            region,
            source,
            Transition.Goal.to(target),
            Transition.Type.EXTERNAL,
            trigger,
            gate,
            response(),
            group));
    return chart;
  }

  /** Enters {@code compound} as its {@code kind} history pseudostate remembers it. */
  public StatechartBuilder<E, X, F> toHistory(S compound, History kind) {
    chart.add(
        new Transition<>(
            region,
            source,
            new Transition.Goal(compound, Optional.of(kind)),
            Transition.Type.EXTERNAL,
            trigger,
            gate,
            response(),
            group));
    return chart;
  }

  /**
   * An internal transition to a descendant of a compound source: the source stays entered. Any
   * other source or target behaves as {@link #to}.
   */
  public StatechartBuilder<E, X, F> within(S target) {
    if (target == source) {
      return to(target);
    }
    chart.add(
        new Transition<>(
            region,
            source,
            Transition.Goal.to(target),
            Transition.Type.INTERNAL,
            trigger,
            gate,
            response(),
            group));
    return chart;
  }

  void leadsTo(S target) {
    if (source == target) {
      internal();
    } else {
      to(target);
    }
  }

  /**
   * An internal transition: the transition's effects and raises run, the state is neither exited
   * nor entered.
   */
  public StatechartBuilder<E, X, F> internal() {
    chart.add(
        new Transition<>(
            region,
            source,
            Transition.Goal.to(source),
            Transition.Type.INTERNAL,
            trigger,
            gate,
            response(),
            group));
    return chart;
  }

  private void requireEvent(String method) {
    if (trigger.eventless()) {
      throw new IllegalStateException(method + " reads an event, an eventless transition has none");
    }
  }

  private Transition.Response<E, F> response() {
    return new Transition.Response<>(List.copyOf(effects), List.copyOf(raises));
  }
}
