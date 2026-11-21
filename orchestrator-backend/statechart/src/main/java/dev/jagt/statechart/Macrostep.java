package dev.jagt.statechart;

import java.util.List;

/**
 * {@code halted}: every region ended in a final state, so nothing moves the chart further. {@code
 * fired}: the transitions the macrostep took, in order.
 */
public record Macrostep<F>(
    Configuration configuration, List<F> effects, boolean halted, List<Fired> fired) {}
