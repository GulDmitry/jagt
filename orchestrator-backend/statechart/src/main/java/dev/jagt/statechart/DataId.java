package dev.jagt.statechart;

public record DataId<S extends Enum<S>>(String name, Class<S> values) {}
