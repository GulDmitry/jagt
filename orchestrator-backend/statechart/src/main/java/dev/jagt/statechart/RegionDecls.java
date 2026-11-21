package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RegionDecls<E, F> {

  private final Map<Enum<?>, List<F>> exits = new LinkedHashMap<>();
  private final Map<Enum<?>, List<F>> entries = new LinkedHashMap<>();
  private final Map<Enum<?>, List<E>> entryRaises = new LinkedHashMap<>();
  private final Map<Enum<?>, List<F>> initialEffects = new LinkedHashMap<>();
  private final Map<Enum<?>, List<Enum<?>>> children = new LinkedHashMap<>();
  private final Map<Enum<?>, Enum<?>> initials = new LinkedHashMap<>();
  private final Map<Enum<?>, List<HistoryState>> histories = new LinkedHashMap<>();

  private final Map<Enum<?>, Choice> choices = new LinkedHashMap<>();
  private final Map<Enum<?>, List<String>> parallels = new LinkedHashMap<>();

  void parallel(Enum<?> state, List<String> regions) {
    parallels.put(state, List.copyOf(regions));
  }

  void choice(Enum<?> state, Choice choice) {
    choices.put(state, choice);
  }

  void exit(Enum<?> state, F effect) {
    exits.computeIfAbsent(state, key -> new ArrayList<>()).add(effect);
  }

  void entry(Enum<?> state, F effect) {
    entries.computeIfAbsent(state, key -> new ArrayList<>()).add(effect);
  }

  void entryRaise(Enum<?> state, E event) {
    entryRaises.computeIfAbsent(state, key -> new ArrayList<>()).add(event);
  }

  void initialEffect(Enum<?> state, F effect) {
    initialEffects.computeIfAbsent(state, key -> new ArrayList<>()).add(effect);
  }

  void history(Enum<?> compound, History kind, Enum<?> target) {
    histories
        .computeIfAbsent(compound, key -> new ArrayList<>())
        .add(new HistoryState(compound, kind, target));
  }

  void compound(Enum<?> parent, Enum<?> initial, List<Enum<?>> kids) {
    children.put(parent, List.copyOf(kids));
    initials.put(parent, initial);
  }

  void compoundByDefault(Enum<?> parent, List<Enum<?>> kids) {
    children.put(parent, List.copyOf(kids));
  }

  Map<Enum<?>, List<F>> exits() {
    return exits;
  }

  Map<Enum<?>, List<F>> entries() {
    return entries;
  }

  Map<Enum<?>, List<E>> entryRaises() {
    return entryRaises;
  }

  Tree<F> tree() {
    return Tree.of(children, initials, initialEffects, histories, choices, parallels);
  }
}
