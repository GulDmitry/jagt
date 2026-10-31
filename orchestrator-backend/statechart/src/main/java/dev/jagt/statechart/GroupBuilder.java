package dev.jagt.statechart;

import java.util.List;

public final class GroupBuilder<E, X, F, S extends Enum<S>> {

    private final ChartBuilder<E, X, F> chart;
    private final List<EdgeBuilder<E, X, F, S>> edges;

    GroupBuilder(ChartBuilder<E, X, F> chart, List<EdgeBuilder<E, X, F, S>> edges) {
        this.chart = chart;
        this.edges = edges;
    }

    public GroupBuilder<E, X, F, S> emit(F effect) {
        edges.forEach(edge -> edge.emit(effect));
        return this;
    }

    /** Every source leads to {@code target}; the one standing there stays. */
    public ChartBuilder<E, X, F> to(S target) {
        edges.forEach(edge -> edge.leadsTo(target));
        return chart;
    }
}
