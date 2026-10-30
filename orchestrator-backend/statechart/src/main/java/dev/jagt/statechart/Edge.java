package dev.jagt.statechart;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

record Edge<E, F>(String region, Enum<?> from, Enum<?> to, String label, Predicate<E> matches, Condition<E> guard,
                        List<Function<E, F>> effects, List<E> raises) {

    boolean takes(Enum<?> state, E event, Others others) {
        return from.equals(state) && matches.test(event) && guard.allows(others, event);
    }

    List<F> effectsFor(E event) {
        return effects.stream().map(effect -> effect.apply(event)).toList();
    }

    String describe() {
        return region + " " + from.name() + "->" + to.name();
    }
}
