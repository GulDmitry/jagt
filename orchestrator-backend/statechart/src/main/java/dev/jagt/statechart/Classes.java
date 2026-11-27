package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What each kind of move does in every state of the regions it is addressed to: taken by an edge,
 * ignored or refused by a declaration. A state with none is a problem.
 */
final class Classes<E, F> {

  private record Key(String region, Class<?> kind, Enum<?> state) {}

  private final Set<Class<?>> kinds;
  private final Map<Class<?>, Set<String>> scope;
  private final List<ClassDecls.Declared> declared;
  private final List<Region<E, F>> regions;
  private final List<Transition<E, F>> transitions;
  private final Map<String, Map<Enum<?>, Set<Class<?>>>> taken = new HashMap<>();
  private final Set<Key> declaredKeys = new HashSet<>();

  Classes(
      Set<Class<?>> kinds,
      Map<Class<?>, Set<String>> scope,
      List<ClassDecls.Declared> declared,
      List<Region<E, F>> regions,
      List<Transition<E, F>> transitions) {
    this.kinds = kinds;
    this.scope = scope;
    this.declared = declared;
    this.regions = regions;
    this.transitions = transitions;
    transitions.forEach(t ->
        taken
            .computeIfAbsent(t.region(), key -> new HashMap<>())
            .computeIfAbsent(t.source(), key -> new HashSet<>())
            .addAll(t.trigger().kinds()));
  }

  static Set<Class<?>> leaves(Class<?> type) {
    if (!type.isSealed()) {
      return Set.of(type);
    }
    return Arrays.stream(type.getPermittedSubclasses())
        .flatMap(permitted -> leaves(permitted).stream())
        .collect(Collectors.toSet());
  }

  List<String> problems() {
    List<String> problems = new ArrayList<>();
    if (kinds.isEmpty()) {
      return problems;
    }
    problems.addAll(edgeProblems());
    problems.addAll(scopeProblems());
    problems.addAll(declarationProblems());
    for (Class<?> kind : sorted(kinds)) {
      for (Region<E, F> region : regions) {
        if (addressed(kind, region.name())) {
          stable(region).stream()
              .filter(state -> !classified(region, state, kind))
              .forEach(state ->
                  problems.add(
                      "move "
                          + kind.getSimpleName()
                          + " has no class in state "
                          + region.name()
                          + "."
                          + state.name()));
        }
      }
    }
    return problems;
  }

  List<String> unclassified(Configuration config, Object event) {
    if (kinds.isEmpty()) {
      return List.of();
    }
    Class<?> kind = event.getClass();
    if (!kinds.contains(kind)) {
      return List.of("move " + kind.getSimpleName() + " is not a kind of move");
    }
    List<String> gaps = new ArrayList<>();
    for (Region<E, F> region : regions) {
      Optional<Enum<?>> at =
          addressed(kind, region.name()) ? config.find(region.name()) : Optional.empty();
      at.filter(state -> !classified(region, state, kind))
          .ifPresent(state -> gaps.add(region.name() + "." + state.name()));
    }
    return gaps;
  }

  private boolean addressed(Class<?> kind, String region) {
    return scope.getOrDefault(kind, Set.of()).contains(region);
  }

  private boolean classified(Region<E, F> region, Enum<?> state, Class<?> kind) {
    return takes(region, state, kind) || declaredKeys.contains(new Key(region.name(), kind, state));
  }

  private List<String> scopeProblems() {
    List<String> problems = new ArrayList<>();
    Set<String> names = regions.stream().map(Region::name).collect(Collectors.toSet());
    scope.forEach((kind, to) -> {
      if (!kinds.contains(kind)) {
        problems.add("move " + kind.getSimpleName() + " is not a kind of move");
      }
      to.stream()
          .filter(name -> !names.contains(name))
          .sorted()
          .forEach(name ->
              problems.add(
                  "move " + kind.getSimpleName() + " is addressed to unknown region " + name));
    });
    return problems;
  }

  private List<String> edgeProblems() {
    List<String> problems = new ArrayList<>();
    for (Transition<E, F> t : transitions) {
      if (t.trigger().eventless()) {
        continue;
      }
      String where = t.region() + "." + t.source().name();
      if (t.trigger().kinds().isEmpty()) {
        problems.add(
            "transition " + t.trigger().label() + " of " + where + " names no kind of move");
      }
      for (Class<?> kind : t.trigger().kinds()) {
        if (kinds.stream().noneMatch(kind::isAssignableFrom)) {
          problems.add(
              "transition of " + where + " names " + kind.getSimpleName() + ", not a kind of move");
        } else if (kinds.contains(kind) && !addressed(kind, t.region())) {
          problems.add(
              "move "
                  + kind.getSimpleName()
                  + " has an edge in region "
                  + t.region()
                  + ", which it is not addressed to");
        }
      }
    }
    return problems.stream().distinct().toList();
  }

  private List<String> declarationProblems() {
    List<String> problems = new ArrayList<>();
    for (ClassDecls.Declared one : declared) {
      String kind = one.kind().getSimpleName();
      Optional<Region<E, F>> region =
          regions.stream().filter(r -> r.name().equals(one.region())).findFirst();
      if (region.isEmpty()) {
        problems.add("move " + kind + " is declared in unknown region " + one.region());
        continue;
      }
      if (!addressed(one.kind(), one.region())) {
        problems.add(
            "move " + kind + " is declared in region " + one.region() + ", not addressed to it");
      }
      if (one.text().isBlank()) {
        problems.add("move " + kind + " in region " + one.region() + " is declared without why");
      }
      one.states().stream()
          .sorted(Comparator.comparingInt(Enum::ordinal))
          .forEach(state -> problems.addAll(stateProblems(region.get(), one, state)));
    }
    return problems;
  }

  private List<String> stateProblems(Region<E, F> region, ClassDecls.Declared one, Enum<?> state) {
    String kind = one.kind().getSimpleName();
    String where = region.name() + "." + state.name();
    if (!stable(region).contains(state)) {
      return List.of("move " + kind + " is declared in " + where + ", which no task stands in");
    }
    if (takes(region, state, one.kind())) {
      return List.of("move " + kind + " is declared in " + where + " and also has an edge there");
    }
    return declaredKeys.add(new Key(region.name(), one.kind(), state))
        ? List.of()
        : List.of("move " + kind + " is declared twice in " + where);
  }

  private boolean takes(Region<E, F> region, Enum<?> state, Class<?> kind) {
    Map<Enum<?>, Set<Class<?>>> own = taken.getOrDefault(region.name(), Map.of());
    return region.tree().lineage(state).stream()
        .anyMatch(level ->
            own.getOrDefault(level, Set.of()).stream()
                .anyMatch(edge -> edge.isAssignableFrom(kind)));
  }

  private static <E, F> List<Enum<?>> stable(Region<E, F> region) {
    return region.states().stream()
        .filter(state -> !region.tree().isCompound(state) && !region.tree().isChoice(state))
        .<Enum<?>>map(state -> state)
        .toList();
  }

  private static List<Class<?>> sorted(Set<Class<?>> set) {
    return set.stream().sorted(Comparator.comparing(Class::getName)).toList();
  }
}
