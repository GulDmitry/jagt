package dev.jagt.statechart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The current state of every active region, a parallel state's regions only while it is, and what
 * each history pseudostate last recorded; a datum is never part of it.
 */
public final class Configuration {

  private final Map<String, Enum<?>> states;
  private final Map<String, Enum<?>> history;

  Configuration(Map<String, Enum<?>> states, Map<String, Enum<?>> history) {
    this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    this.history = Collections.unmodifiableMap(new LinkedHashMap<>(history));
  }

  public <S extends Enum<S>> S get(RegionId<S> region) {
    return region
        .states()
        .cast(
            find(region.name())
                .orElseThrow(() -> new IllegalArgumentException("no region " + region.name())));
  }

  public Map<String, String> toPlain() {
    return names(states);
  }

  /**
   * History pseudostate to the last leaf its compound state was exited from; empty at the start.
   */
  public Map<String, String> history() {
    return names(history);
  }

  Optional<Enum<?>> find(String region) {
    return Optional.ofNullable(states.get(region));
  }

  Optional<Enum<?>> stored(String key) {
    return Optional.ofNullable(history.get(key));
  }

  Configuration with(String region, Enum<?> state) {
    Map<String, Enum<?>> next = new LinkedHashMap<>(states);
    next.put(region, state);
    return new Configuration(next, history);
  }

  Configuration without(String region) {
    Map<String, Enum<?>> next = new LinkedHashMap<>(states);
    next.remove(region);
    return new Configuration(next, history);
  }

  Configuration remembering(String key, Enum<?> leaf) {
    Map<String, Enum<?>> next = new LinkedHashMap<>(history);
    next.put(key, leaf);
    return new Configuration(states, next);
  }

  private static Map<String, String> names(Map<String, Enum<?>> source) {
    Map<String, String> plain = new LinkedHashMap<>();
    source.forEach((key, state) -> plain.put(key, state.name()));
    return plain;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Configuration that
        && states.equals(that.states)
        && history.equals(that.history);
  }

  @Override
  public int hashCode() {
    return 31 * states.hashCode() + history.hashCode();
  }

  @Override
  public String toString() {
    return history.isEmpty() ? toPlain().toString() : toPlain() + " history " + history();
  }
}
