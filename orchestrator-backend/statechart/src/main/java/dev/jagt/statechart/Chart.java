package dev.jagt.statechart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regions run in parallel and a step is pure. Within a microstep every region picks its first enabled transition
 * against the configuration from before it, exits are emitted before transition effects, and raised events run
 * to completion in raise order. A transition to its own state still exits it; an internal one ({@code stay}) does not. A derived region is read from the
 * facts the caller passes, the same for every raised event of the step.
 */
public final class Chart<E, X, F> {

    static final int DEFAULT_CEILING = 100;

    private final List<Stored<F>> stored;
    private final List<Derived<X>> derived;
    private final List<Edge<E, F>> transitions;
    private final int ceiling;

    Chart(List<Stored<F>> stored, List<Derived<X>> derived, List<Edge<E, F>> transitions, int ceiling) {
        this.stored = stored;
        this.derived = derived;
        this.transitions = transitions;
        this.ceiling = ceiling;
    }

    public static <E, X, F> ChartBuilder<E, X, F> builder() {
        return new ChartBuilder<>();
    }

    public Configuration initial() {
        Map<String, Enum<?>> states = new LinkedHashMap<>();
        stored.forEach(region -> states.put(region.name(), region.initial()));
        return new Configuration(states);
    }

    public Configuration configuration(Map<String, String> plain) {
        List<String> problems = new ArrayList<>();
        plain.keySet().stream().filter(name -> stored.stream().noneMatch(region -> region.name().equals(name)))
                .forEach(name -> problems.add("no stored region " + name));
        Map<String, Enum<?>> states = new LinkedHashMap<>();
        for (Stored<F> region : stored) {
            Optional<? extends Enum<?>> state = Optional.ofNullable(plain.get(region.name()))
                    .flatMap(name -> region.states().stream().filter(s -> s.name().equals(name)).findFirst());
            state.ifPresentOrElse(s -> states.put(region.name(), s), () -> problems.add(
                    "region " + region.name() + " has no known state, got " + plain.get(region.name())));
        }
        if (!problems.isEmpty()) {
            throw new InvalidConfiguration(problems);
        }
        return new Configuration(states);
    }

    public ChartStep<F> step(Configuration start, X facts, E event) {
        Configuration config = start;
        List<F> effects = new ArrayList<>();
        List<String> moves = new ArrayList<>();
        Deque<E> queue = new ArrayDeque<>(List.of(event));
        int raised = 0;
        while (!queue.isEmpty()) {
            E current = queue.poll();
            List<Edge<E, F>> taken = enabled(config, facts, current);
            taken.stream().filter(t -> !t.internal()).forEach(t -> effects.addAll(exitEffects(t)));
            for (Edge<E, F> t : taken) {
                effects.addAll(t.effectsFor(current));
                queue.addAll(t.raises());
                moves.add(t.describe());
                raised += t.raises().size();
            }
            if (raised > ceiling) {
                throw new RaiseCeilingExceeded(ceiling, moves);
            }
            for (Edge<E, F> t : taken) {
                config = config.with(t.region(), t.to());
            }
        }
        return new ChartStep<>(config, List.copyOf(effects));
    }

    public String render() {
        return Mermaid.render(stored, derived, transitions);
    }

    private List<Edge<E, F>> enabled(Configuration config, X facts, E event) {
        List<Edge<E, F>> taken = new ArrayList<>();
        for (Stored<F> region : stored) {
            Others others = new Others(region.name(), name -> stateOf(config, facts, name));
            Enum<?> state = config.find(region.name()).orElseThrow();
            transitions.stream().filter(t -> t.region().equals(region.name()) && t.takes(state, event, others))
                    .findFirst().ifPresent(taken::add);
        }
        return taken;
    }

    private Enum<?> stateOf(Configuration config, X facts, String region) {
        return config.find(region).or(() -> derived.stream().filter(d -> d.name().equals(region)).findFirst()
                        .map(d -> d.compute().apply(facts)))
                .orElseThrow(() -> new IllegalArgumentException("no region " + region));
    }

    private List<F> exitEffects(Edge<E, F> transition) {
        return stored.stream().filter(region -> region.name().equals(transition.region())).findFirst()
                .map(region -> region.exitEffects(transition.from())).orElse(List.of());
    }
}
