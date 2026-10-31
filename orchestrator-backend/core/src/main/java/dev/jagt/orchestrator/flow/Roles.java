package dev.jagt.orchestrator.flow;

import java.util.List;
import java.util.Optional;

/** The checker roles a task's chart is composed of, and whether one may be. */
public final class Roles {

    private Roles() {
    }

    /** Sets, once, the roles every task is stepped on; a second call must name the same ones. */
    public static void install(List<String> checkers) {
        TaskChart.install(new Composition(checkers.stream().<Role>map(CheckerRegion::new).toList()));
    }

    /** Why the chart refuses {@code checker} composed beside the core regions, or empty where it stands. */
    public static Optional<String> lint(String checker) {
        try {
            TaskChart.of(new Composition(List.of(new CheckerRegion(checker))));
            return Optional.empty();
        } catch (RuntimeException refused) {
            return Optional.of(String.valueOf(refused.getMessage()));
        }
    }
}
