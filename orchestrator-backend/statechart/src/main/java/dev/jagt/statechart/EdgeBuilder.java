package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class EdgeBuilder<E, X, F, S extends Enum<S>> {

    private final ChartBuilder<E, X, F> chart;
    private final String region;
    private final S from;
    private final String label;
    private final Predicate<E> matches;
    private Condition<E> guard = (others, event) -> true;
    private final List<Function<E, F>> effects = new ArrayList<>();
    private final List<E> raises = new ArrayList<>();

    EdgeBuilder(ChartBuilder<E, X, F> chart, String region, S from, String label, Predicate<E> matches) {
        this.chart = chart;
        this.region = region;
        this.from = from;
        this.label = label;
        this.matches = matches;
    }

    public EdgeBuilder<E, X, F, S> when(Condition<E> allowed) {
        guard = allowed;
        return this;
    }

    public EdgeBuilder<E, X, F, S> emit(F effect) {
        effects.add(event -> effect);
        return this;
    }

    public EdgeBuilder<E, X, F, S> emitFor(Function<E, F> effect) {
        effects.add(effect);
        return this;
    }

    public EdgeBuilder<E, X, F, S> raise(E event) {
        raises.add(event);
        return this;
    }

    public ChartBuilder<E, X, F> to(S target) {
        chart.add(new Edge<>(region, from, target, label, matches, guard, List.copyOf(effects),
                List.copyOf(raises)));
        return chart;
    }
}
