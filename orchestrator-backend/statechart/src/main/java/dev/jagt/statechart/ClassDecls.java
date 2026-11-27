package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ClassDecls {

  record Declared(
      String region, Class<?> kind, Set<Enum<?>> states, boolean refused, String text) {}

  private Optional<Class<?>> root = Optional.empty();
  private final Map<Class<?>, Set<String>> scope = new LinkedHashMap<>();
  private final List<Declared> declared = new ArrayList<>();

  void moves(Class<?> type) {
    root = Optional.of(type);
  }

  void addressed(Class<?> kind, List<String> regions) {
    scope.computeIfAbsent(kind, key -> new LinkedHashSet<>()).addAll(regions);
  }

  void declare(
      String region, Class<?> kind, Set<? extends Enum<?>> states, boolean refused, String text) {
    Declared one = new Declared(region, kind, Set.copyOf(states), refused, text);
    if (!declared.contains(one)) {
      declared.add(one);
    }
  }

  <E, F> Classes<E, F> of(List<Region<E, F>> regions, List<Transition<E, F>> transitions) {
    return new Classes<>(
        root.map(Classes::leaves).orElse(Set.of()),
        Map.copyOf(scope),
        List.copyOf(declared),
        regions,
        transitions);
  }
}
