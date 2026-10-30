package dev.jagt.statechart;

import java.util.List;

public record Step<F>(Configuration configuration, List<F> effects) {
}
