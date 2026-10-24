package dev.jagt.orchestrator.stand.mutation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.Script;
import dev.jagt.orchestrator.stand.StandMode;
import dev.jagt.orchestrator.stand.StandOutcome;
import dev.jagt.orchestrator.stand.StandRun;
import java.nio.file.Files;
import java.util.stream.Stream;
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

            StandOutcome outcome = stand.drive(applied.events().stream().reduce(Script.to("ABC-5", "DEPLOYED"),
                    Script::with, (first, second) -> second));

            assertThat(outcome).as(mutation.holds() + "\n" + outcome.describe()).satisfiesAnyOf(
                    deployed -> assertThat(deployed.status()).isEqualTo("DEPLOYED"),
                    handedOver -> assertThat(handedOver.withTheHuman()).isTrue(),
                    answered -> assertThat(answered.withTheCodeHost() && stand.store().awaitsTheReviewer()).isTrue());
            assertThat(stand.record().histories()).as("the task was never closed: " + outcome.describe()).isNotEmpty();
            assertThat(stand.violations()).as(mutation.holds() + "\n" + outcome.describe()).isEmpty();
            stand.passed();
        }
    }

    static Stream<Arguments> runs() {
        return MutationRuns.of("mutation");
    }
}
