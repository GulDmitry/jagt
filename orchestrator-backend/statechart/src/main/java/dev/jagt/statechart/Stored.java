package dev.jagt.statechart;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record Stored<F>(String name, Class<? extends Enum<?>> type, Enum<?> initial, Set<Enum<?>> finals,
                 Map<Enum<?>, List<F>> exits, Optional<Scope> scope) {

    /** The state of another region this one lives inside; active only while that region stands in it. */
    record Scope(String parent, Enum<?> state) {
    }

    boolean scopedTo(String region, Enum<?> state) {
        return scope.filter(own -> own.parent().equals(region) && own.state().equals(state)).isPresent();
    }

    List<F> exitEffects(Enum<?> state) {
        return exits.getOrDefault(state, List.of());
    }

    List<? extends Enum<?>> states() {
        return List.of(type.getEnumConstants());
    }
}
