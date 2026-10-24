package dev.jagt.orchestrator.stand;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public final class StandRuns {

    public record Run(StandMode mode, int index) {
    }

    private StandRuns() {
    }

    public static Stream<Arguments> of(String scenario) {
        return selected(scenario, System.getProperty("stand.scenario", ""), System.getProperty("stand.modes", ""),
                Integer.getInteger("stand.runs", 1)).stream().map(run -> Arguments.of(run.mode(), run.index()));
    }

    public static List<Run> selected(String scenario, String scenarios, String modes, int runs) {
        boolean chosen = scenarios.isBlank() || Arrays.stream(scenarios.split(",")).map(String::strip)
                .anyMatch(scenario::equals);
        if (!chosen) {
            return List.of();
        }
        return StandMode.selected(modes).stream()
                .flatMap(mode -> IntStream.rangeClosed(1, runs).mapToObj(index -> new Run(mode, index))).toList();
    }
}
