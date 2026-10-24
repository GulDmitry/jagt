package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandMode;
import dev.jagt.orchestrator.stand.StandRuns;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public final class MutationRuns {

    public record Run(Mutation mutation, long seed, StandMode mode, int index) {
    }

    private MutationRuns() {
    }

    public static Stream<Arguments> of(String scenario) {
        if (!StandRuns.chosen(scenario, System.getProperty("stand.scenario", ""))) {
            return Stream.of();
        }
        String seed = System.getProperty("stand.seed", "");
        return selected(MutationBook.shipped(), System.getProperty("stand.mutations", ""),
                seed.isBlank() ? System.nanoTime() : Long.parseLong(seed), System.getProperty("stand.modes", ""),
                Integer.getInteger("stand.runs", 1)).stream()
                .map(run -> Arguments.of(run.mutation().name(), run.seed(), run.mode(), run.index()));
    }

    public static List<Run> selected(MutationBook book, String names, long seed, String modes, int runs) {
        List<Mutation> chosen = names.isBlank() ? List.of(book.pick(seed)) : book.named(names);
        return chosen.stream().flatMap(mutation -> StandMode.selected(modes).stream()
                .flatMap(mode -> IntStream.rangeClosed(1, runs).mapToObj(index -> new Run(mutation, seed, mode, index))))
                .toList();
    }
}
