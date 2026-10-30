package dev.jagt.statechart;

import java.util.List;

public record ChartStep<F>(Configuration configuration, List<F> effects) {
}
