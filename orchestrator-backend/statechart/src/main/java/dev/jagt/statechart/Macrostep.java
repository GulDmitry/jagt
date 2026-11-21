package dev.jagt.statechart;

import java.util.List;

/** {@code halted}: every region ended in a final state, so nothing moves the chart further. */
public record Macrostep<F>(Configuration configuration, List<F> effects, boolean halted) {}
