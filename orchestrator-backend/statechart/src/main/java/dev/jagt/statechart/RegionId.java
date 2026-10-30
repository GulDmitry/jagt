package dev.jagt.statechart;

public record RegionId<S extends Enum<S>>(String name, Class<S> states) {
}
