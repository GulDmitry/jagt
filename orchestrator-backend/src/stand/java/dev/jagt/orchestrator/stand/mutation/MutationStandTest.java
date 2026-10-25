package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.Script;
import dev.jagt.orchestrator.stand.StandMode;
import dev.jagt.orchestrator.stand.StandOutcome;
import dev.jagt.orchestrator.stand.StandRun;
import dev.jagt.orchestrator.stand.invariant.RunRecord;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("model")
class MutationStandTest {

    private static final String SEED_TICKET = """
            Called without an argument, ./greet.sh prints "Hello, " today. It must print exactly "Hello, World" \
            instead; with an argument it stays "Hello, <name>". Add a check for the no-argument case to ./check.sh, \
            which must keep passing.""";

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("runs")
    @Execution(ExecutionMode.CONCURRENT)
    void aMutatedRunKeepsTheFrame(String name, long seed, StandMode mode, int run) throws Exception {
        Mutation mutation = MutationBook.shipped().named(name).getFirst();
        try (StandRun stand = StandRun.start("mutation-" + name, mode)) {
            Files.writeString(stand.root().resolve("mutation.txt"), name + " seed " + seed + "\nholds: " + mutation.holds());
            Files.writeString(stand.root().resolve("ticket-seed.md"), SEED_TICKET);
            MutationPlan.Applied applied = new MutationPlan(new HaikuRewrite(stand.root())).apply(mutation, SEED_TICKET);
            Files.writeString(stand.root().resolve("ticket.md"), applied.ticket());
            stand.store().seedTicket("ABC-5", "Greet the world by default", applied.ticket(), "To Do");

            Script script = applied.events().stream().reduce(Script.to("ABC-5", "DEPLOYED"), Script::with,
                    (first, second) -> second);
            StandOutcome outcome = stand.drive(applied.expectsDeployConflict() ? script.expectingDeployConflict() : script);

            RunRecord record = stand.record();
            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(MutationProgress.lacking(mutation.kind(), outcome, record.history("ABC-5"),
                        record.misses().size(), stand.store().awaitsTheReviewer(), conflictRelayed(record), applied.expectsDeployConflict()))
                        .as(mutation.holds() + "\n" + outcome.describe()).isEmpty();
                soft.assertThat(stand.violations()).as(mutation.holds() + "\n" + outcome.describe()).isEmpty();
            });
            stand.passed();
        }
    }

    private static boolean conflictRelayed(RunRecord record) {
        return record.timeline().relays().stream().anyMatch(relay -> relay.steps().contains("conflict"));
    }

    static Stream<Arguments> runs() {
        return MutationRuns.of("mutation");
    }
}
