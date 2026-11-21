package dev.jagt.statechart;

import java.util.function.Function;

record Datum<X>(String name, Function<X, ? extends Enum<?>> compute) {}
