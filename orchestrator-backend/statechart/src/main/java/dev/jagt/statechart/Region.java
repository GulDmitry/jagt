package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record Region<E, F>(
    String name,
    Class<? extends Enum<?>> type,
    Enum<?> initial,
    Set<Enum<?>> finals,
    Map<Enum<?>, List<F>> exits,
    Map<Enum<?>, List<F>> entries,
    Map<Enum<?>, List<E>> entryRaises,
    Tree<F> tree) {

  List<F> exitEffects(Enum<?> state) {
    return exits.getOrDefault(state, List.of());
  }

  List<F> entryEffects(Traversal.Entry entry) {
    List<F> effects = new ArrayList<>(entries.getOrDefault(entry.state(), List.of()));
    if (entry.byDefault()) {
      effects.addAll(tree.initialEffects().getOrDefault(entry.state(), List.of()));
    }
    return effects;
  }

  List<E> raisesOnEntry(Enum<?> state) {
    return entryRaises.getOrDefault(state, List.of());
  }

  Optional<Enum<?>> remembered(String key, String leaf) {
    return tree.histories().values().stream()
        .flatMap(List::stream)
        .filter(history -> history.key(name).equals(key))
        .flatMap(history ->
            states().stream()
                .filter(
                    state -> state.name().equals(leaf) && tree.inside(state, history.compound()))
                .<Enum<?>>map(state -> state))
        .findFirst();
  }

  List<Traversal.Entry> startEntries() {
    List<Traversal.Entry> entries = new ArrayList<>();
    tree.ancestors(initial)
        .reversed()
        .forEach(state -> entries.add(new Traversal.Entry(state, false)));
    tree.descent(initial).forEach(state -> entries.add(new Traversal.Entry(state, true)));
    return entries;
  }

  Enum<?> startLeaf() {
    return tree.leaf(initial);
  }

  List<? extends Enum<?>> states() {
    return List.of(type.getEnumConstants());
  }
}
