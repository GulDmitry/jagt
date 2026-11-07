package dev.jagt.statechart;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

record Edge<E, F>(
    String region,
    Enum<?> from,
    Enum<?> to,
    Trigger<E> trigger,
    Condition<E> guard,
    Response<E, F> response,
    int group) {

  record Trigger<E>(String label, Predicate<E> matches) {}

  record Response<E, F>(List<Function<E, F>> effects, List<E> raises, boolean internal) {}

  boolean takes(Enum<?> state, E event, Others others) {
    return from.equals(state) && trigger.matches().test(event) && guard.allows(others, event);
  }

  List<F> effectsFor(E event) {
    return response.effects().stream().map(effect -> effect.apply(event)).toList();
  }

  String describe() {
    return region + " " + from.name() + "->" + to.name();
  }
}
