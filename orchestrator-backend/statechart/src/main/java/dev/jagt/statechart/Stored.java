package dev.jagt.statechart;

import java.util.List;
import java.util.Map;
import java.util.Set;

record Stored<F>(String name, Class<? extends Enum<?>> type, Enum<?> initial, Set<Enum<?>> finals,
                 Map<Enum<?>, List<F>> exits) {

    List<F> exitEffects(Enum<?> state) {
        return exits.getOrDefault(state, List.of());
    }

    List<? extends Enum<?>> states() {
        return List.of(type.getEnumConstants());
    }
}
