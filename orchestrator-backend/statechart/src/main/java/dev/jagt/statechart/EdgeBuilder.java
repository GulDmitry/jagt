package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EdgeBuilder<E, X, F, S extends Enum<S>> {

  private final ChartBuilder<E, X, F> chart;
  private final String region;
  private final S from;
  private final Edge.Trigger<E> trigger;
  private Condition<E> guard = (others, event) -> true;
  private final List<Function<E, F>> effects = new ArrayList<>();
  private final List<E> raises = new ArrayList<>();
  private int group;

  EdgeBuilder(ChartBuilder<E, X, F> chart, String region, S from, Edge.Trigger<E> trigger) {
    this.chart = chart;
    this.region = region;
    this.from = from;
    this.trigger = trigger;
  }

  EdgeBuilder<E, X, F, S> inGroup(int id) {
    group = id;
    return this;
  }

  public EdgeBuilder<E, X, F, S> when(Condition<E> allowed) {
    guard = allowed;
    return this;
  }

  public EdgeBuilder<E, X, F, S> emit(F effect) {
    effects.add(event -> effect);
    return this;
  }

  public EdgeBuilder<E, X, F, S> emitFor(Function<E, F> effect) {
    effects.add(effect);
    return this;
  }

  public EdgeBuilder<E, X, F, S> raise(E event) {
    raises.add(event);
    return this;
  }

  public ChartBuilder<E, X, F> to(S target) {
    chart.add(new Edge<>(region, from, target, trigger, guard, response(false), group));
    return chart;
  }

  void leadsTo(S target) {
    if (from == target) {
      stay();
    } else {
      to(target);
    }
  }

  /**
   * An internal transition: the edge's effects and raises run, the state is neither exited nor
   * entered.
   */
  public ChartBuilder<E, X, F> stay() {
    chart.add(new Edge<>(region, from, from, trigger, guard, response(true), group));
    return chart;
  }

  private Edge.Response<E, F> response(boolean internal) {
    return new Edge.Response<>(List.copyOf(effects), List.copyOf(raises), internal);
  }
}
