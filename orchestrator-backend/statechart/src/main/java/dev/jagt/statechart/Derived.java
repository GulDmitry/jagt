package dev.jagt.statechart;

import java.util.function.Function;

record Derived<E>(String name, Function<E, ? extends Enum<?>> compute) {
}
