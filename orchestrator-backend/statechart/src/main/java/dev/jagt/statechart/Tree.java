package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record Tree<F>(
    Map<Enum<?>, List<Enum<?>>> children,
    Map<Enum<?>, Enum<?>> parents,
    Map<Enum<?>, Enum<?>> initials,
    Map<Enum<?>, List<F>> initialEffects,
    Map<Enum<?>, List<HistoryState>> histories,
    Map<Enum<?>, Choice> choices,
    Map<Enum<?>, List<String>> parallels) {

  static <F> Tree<F> of(
      Map<Enum<?>, List<Enum<?>>> children,
      Map<Enum<?>, Enum<?>> explicitInitials,
      Map<Enum<?>, List<F>> initialEffects,
      Map<Enum<?>, List<HistoryState>> histories,
      Map<Enum<?>, Choice> choices,
      Map<Enum<?>, List<String>> parallels) {
    Map<Enum<?>, Enum<?>> parents = new LinkedHashMap<>();
    Map<Enum<?>, Enum<?>> initials = new LinkedHashMap<>();
    children.forEach((parent, kids) -> {
      kids.forEach(kid -> parents.putIfAbsent(kid, parent));
      initials.put(
          parent,
          explicitInitials.getOrDefault(
              parent, kids.stream().min(Comparator.comparingInt(Enum::ordinal)).orElseThrow()));
    });
    return new Tree<>(
        Map.copyOf(children),
        Map.copyOf(parents),
        Map.copyOf(initials),
        Map.copyOf(initialEffects),
        Map.copyOf(histories),
        Map.copyOf(choices),
        Map.copyOf(parallels));
  }

  List<String> regionsOf(Enum<?> state) {
    return parallels.getOrDefault(state, List.of());
  }

  boolean isChoice(Enum<?> state) {
    return choices.containsKey(state);
  }

  Choice choice(Enum<?> state) {
    return Optional.ofNullable(choices.get(state)).orElseThrow();
  }

  List<HistoryState> historiesOf(Enum<?> compound) {
    return histories.getOrDefault(compound, List.of());
  }

  Optional<HistoryState> historyState(Enum<?> compound, History kind) {
    return historiesOf(compound).stream().filter(h -> h.kind() == kind).findFirst();
  }

  boolean inside(Enum<?> state, Enum<?> compound) {
    return !state.equals(compound) && lineage(state).contains(compound);
  }

  Traversal restore(Traversal base, HistoryState history, Optional<Enum<?>> stored) {
    Enum<?> compound = history.compound();
    Enum<?> resume =
        stored
            .map(state -> history.kind() == History.DEEP ? state : childOf(compound, state))
            .orElse(history.target());
    List<Enum<?>> climb =
        new ArrayList<>(lineage(resume).stream().takeWhile(s -> !s.equals(compound)).toList());
    climb.add(compound);
    Collections.reverse(climb);
    climb.removeLast();
    List<Traversal.Entry> entries =
        new ArrayList<>(
            base.entries().stream().takeWhile(e -> !e.state().equals(compound)).toList());
    climb.forEach(state -> entries.add(new Traversal.Entry(state, false)));
    List<Enum<?>> down = descent(resume);
    down.forEach(state -> entries.add(new Traversal.Entry(state, true)));
    return new Traversal(base.exits(), entries, down.getLast());
  }

  private Enum<?> childOf(Enum<?> compound, Enum<?> state) {
    return lineage(state).stream()
        .filter(s -> parent(s).filter(compound::equals).isPresent())
        .findFirst()
        .orElseThrow();
  }

  List<Enum<?>> kids(Enum<?> state) {
    return children.getOrDefault(state, List.of());
  }

  Enum<?> initial(Enum<?> state) {
    return Optional.ofNullable(initials.get(state)).orElseThrow();
  }

  boolean isCompound(Enum<?> state) {
    return children.containsKey(state);
  }

  Optional<Enum<?>> parent(Enum<?> state) {
    return Optional.ofNullable(parents.get(state));
  }

  List<Enum<?>> ancestors(Enum<?> state) {
    List<Enum<?>> up = new ArrayList<>();
    // The bound only stops a cycle; one step either way changes nothing observable.
    Optional<Enum<?>> next = parent(state);
    while (next.isPresent() && up.size() <= parents.size()) {
      up.add(next.get());
      next = parent(next.get());
    }
    return up;
  }

  List<Enum<?>> lineage(Enum<?> state) {
    List<Enum<?>> line = new ArrayList<>(List.of(state));
    line.addAll(ancestors(state));
    return line;
  }

  List<Enum<?>> descent(Enum<?> state) {
    List<Enum<?>> down = new ArrayList<>(List.of(state));
    Enum<?> last = state;
    // The bound only stops a cycle; one step either way changes nothing observable.
    while (isCompound(last) && down.size() <= initials.size() + 1) {
      last = initial(last);
      down.add(last);
    }
    return down;
  }

  Enum<?> leaf(Enum<?> state) {
    return descent(state).getLast();
  }

  List<Enum<?>> entered(Enum<?> state) {
    List<Enum<?>> all = new ArrayList<>(ancestors(state));
    all.addAll(descent(state));
    return all;
  }

  boolean cyclic(Enum<?> state) {
    return ancestors(state).contains(state);
  }

  Traversal move(Enum<?> source, Enum<?> target, Enum<?> leaf) {
    List<Enum<?>> targetUp = ancestors(target);
    Optional<Enum<?>> lcca = ancestors(source).stream().filter(targetUp::contains).findFirst();
    return traverse(lcca, target, leaf);
  }

  Traversal moveWithin(Enum<?> source, Enum<?> target, Enum<?> leaf) {
    return traverse(Optional.of(source), target, leaf);
  }

  private Traversal traverse(Optional<Enum<?>> lcca, Enum<?> target, Enum<?> leaf) {
    List<Enum<?>> above =
        new ArrayList<>(
            ancestors(target).stream().takeWhile(a -> lcca.filter(a::equals).isEmpty()).toList());
    Collections.reverse(above);
    List<Traversal.Entry> entries = new ArrayList<>();
    above.forEach(state -> entries.add(new Traversal.Entry(state, false)));
    List<Enum<?>> down = descent(target);
    down.forEach(state -> entries.add(new Traversal.Entry(state, true)));
    List<Enum<?>> exits =
        lineage(leaf).stream().takeWhile(s -> lcca.filter(s::equals).isEmpty()).toList();
    return new Traversal(exits, entries, down.getLast());
  }
}
