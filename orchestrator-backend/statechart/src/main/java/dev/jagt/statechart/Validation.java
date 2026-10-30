package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class Validation {

    private Validation() {
    }

    static <E, X, F> List<String> problems(List<String> names, List<Stored<F>> stored, List<Derived<X>> derived,
                                        List<Edge<E, F>> transitions) {
        List<String> problems = new ArrayList<>();
        names.stream().distinct().filter(name -> Collections.frequency(names, name) > 1)
                .forEach(name -> problems.add("region " + name + " is declared more than once"));
        Set<String> storedNames = new LinkedHashSet<>(stored.stream().map(Stored::name).toList());
        Set<String> derivedNames = new LinkedHashSet<>(derived.stream().map(Derived::name).toList());
        transitions.stream().map(Edge::region).distinct().filter(name -> !storedNames.contains(name))
                .forEach(name -> problems.add(derivedNames.contains(name)
                        ? "derived region " + name + " has a transition" : "transition on unknown region " + name));
        stored.forEach(region -> problems.addAll(problemsOf(region, transitions)));
        return problems;
    }

    private static <E, X, F> List<String> problemsOf(Stored<F> region, List<Edge<E, F>> transitions) {
        List<Edge<E, F>> own = transitions.stream().filter(t -> t.region().equals(region.name())).toList();
        Set<Enum<?>> reached = reachable(region.initial(), own);
        return region.states().stream().flatMap(state -> Stream.of(
                        !region.finals().contains(state) && own.stream().noneMatch(t -> t.from().equals(state))
                                ? "state " + region.name() + "." + state.name() + " has no way out" : "",
                        reached.contains(state) ? "" : "state " + region.name() + "." + state.name()
                                + " is unreachable"))
                .filter(problem -> !problem.isEmpty()).toList();
    }

    private static <E, F> Set<Enum<?>> reachable(Enum<?> initial, List<Edge<E, F>> own) {
        Set<Enum<?>> reached = new LinkedHashSet<>(List.of(initial));
        List<Enum<?>> frontier = new ArrayList<>(reached);
        while (!frontier.isEmpty()) {
            Enum<?> state = frontier.removeFirst();
            own.stream().filter(t -> t.from().equals(state)).map(Edge::to).filter(reached::add)
                    .forEach(frontier::add);
        }
        return reached;
    }
}
