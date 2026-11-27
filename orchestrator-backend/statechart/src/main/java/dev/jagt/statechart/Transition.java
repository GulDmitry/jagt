package dev.jagt.statechart;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

record Transition<E, F>(
    String region,
    Enum<?> source,
    Goal goal,
    Type type,
    Trigger<E> trigger,
    Gate<E> gate,
    Response<E, F> response,
    int group) {

  record Goal(Enum<?> state, Optional<History> via) {

    static Goal to(Enum<?> state) {
      return new Goal(state, Optional.empty());
    }
  }

  enum Type {
    EXTERNAL,
    INTERNAL
  }

  record Trigger<E>(String label, Predicate<E> matches, boolean eventless, Set<Class<?>> kinds) {

    boolean fires(Optional<E> event) {
      return event.map(e -> !eventless && matches.test(e)).orElse(eventless);
    }
  }

  @FunctionalInterface
  interface Gate<E> {

    boolean allows(Active active, Optional<E> event);
  }

  record Response<E, F>(List<Function<Optional<E>, F>> effects, List<E> raises) {}

  boolean takes(Enum<?> state, Optional<E> event, Active active) {
    return source.equals(state) && trigger.fires(event) && gate.allows(active, event);
  }

  Enum<?> target() {
    return goal.state();
  }

  boolean internal() {
    return type == Type.INTERNAL;
  }

  Transition<E, F> retargeted(Enum<?> state) {
    return new Transition<>(region, source, Goal.to(state), type, trigger, gate, response, group);
  }

  List<F> effectsFor(Optional<E> event) {
    return response.effects().stream().map(effect -> effect.apply(event)).toList();
  }

  Fired fired() {
    return new Fired(region, source.name(), target().name(), trigger.label(), trigger.eventless());
  }
}
