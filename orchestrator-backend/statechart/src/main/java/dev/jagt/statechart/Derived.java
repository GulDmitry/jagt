package dev.jagt.statechart;

import java.util.function.Function;

record Derived<X>(String name, Function<X, ? extends Enum<?>> compute) {
}
