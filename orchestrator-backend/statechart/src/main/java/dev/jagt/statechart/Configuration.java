package dev.jagt.statechart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The current state of every stored region; a derived region is never part of it. */
public final class Configuration {

    private final Map<String, Enum<?>> states;

    Configuration(Map<String, Enum<?>> states) {
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    public <S extends Enum<S>> S get(RegionId<S> region) {
        return region.states().cast(find(region.name()).orElseThrow(
                () -> new IllegalArgumentException("no stored region " + region.name())));
    }

    public Map<String, String> toPlain() {
        Map<String, String> plain = new LinkedHashMap<>();
        states.forEach((region, state) -> plain.put(region, state.name()));
        return plain;
    }

    Optional<Enum<?>> find(String region) {
        return Optional.ofNullable(states.get(region));
    }

    Configuration with(String region, Enum<?> state) {
        Map<String, Enum<?>> next = new LinkedHashMap<>(states);
        next.put(region, state);
        return new Configuration(next);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Configuration that && states.equals(that.states);
    }

    @Override
    public int hashCode() {
        return states.hashCode();
    }

    @Override
    public String toString() {
        return toPlain().toString();
    }
}
