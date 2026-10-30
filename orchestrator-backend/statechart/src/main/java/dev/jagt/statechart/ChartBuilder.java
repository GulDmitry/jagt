package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ChartBuilder<E, X, F> {

    private record Pending(String name, Class<? extends Enum<?>> type, Enum<?> initial, Set<Enum<?>> finals) {
    }

    private final List<Pending> regions = new ArrayList<>();
    private final List<Derived<X>> derived = new ArrayList<>();
    private final Map<String, Map<Enum<?>, List<F>>> exits = new HashMap<>();
    private final List<Edge<E, F>> transitions = new ArrayList<>();
    private int ceiling = Chart.DEFAULT_CEILING;

    ChartBuilder() {
    }

    public <S extends Enum<S>> ChartBuilder<E, X, F> region(RegionId<S> id, S initial) {
        return region(id, initial, Set.of());
    }

    public <S extends Enum<S>> ChartBuilder<E, X, F> region(RegionId<S> id, S initial, Set<S> finals) {
        regions.add(new Pending(id.name(), id.states(), initial, new LinkedHashSet<Enum<?>>(finals)));
        return this;
    }

    public <S extends Enum<S>> ChartBuilder<E, X, F> derived(RegionId<S> id, Function<X, S> compute) {
        derived.add(new Derived<>(id.name(), compute));
        return this;
    }

    public <S extends Enum<S>> ChartBuilder<E, X, F> onExit(RegionId<S> id, S state, F effect) {
        exits.computeIfAbsent(id.name(), name -> new HashMap<>()).computeIfAbsent(state, key -> new ArrayList<>())
                .add(effect);
        return this;
    }

    public <S extends Enum<S>> EdgeBuilder<E, X, F, S> on(RegionId<S> id, S from, Class<? extends E> type) {
        return new EdgeBuilder<>(this, id.name(), from, type.getSimpleName(), type::isInstance);
    }

    public <S extends Enum<S>> EdgeBuilder<E, X, F, S> onMatching(RegionId<S> id, S from, String label,
                                                                    Predicate<E> matches) {
        return new EdgeBuilder<>(this, id.name(), from, label, matches);
    }

    public ChartBuilder<E, X, F> raiseCeiling(int events) {
        ceiling = events;
        return this;
    }

    public Chart<E, X, F> build() {
        List<Stored<F>> stored = regions.stream().map(region -> new Stored<>(region.name(), region.type(),
                region.initial(), region.finals(),
                Map.<Enum<?>, List<F>>copyOf(exits.getOrDefault(region.name(), Map.of())))).toList();
        List<String> names = new ArrayList<>(regions.stream().map(Pending::name).toList());
        derived.forEach(region -> names.add(region.name()));
        List<String> problems = Validation.problems(names, stored, derived, transitions);
        if (!problems.isEmpty()) {
            throw new ChartInvalid(problems);
        }
        return new Chart<>(stored, List.copyOf(derived), List.copyOf(transitions), ceiling);
    }

    void add(Edge<E, F> transition) {
        transitions.add(transition);
    }
}
