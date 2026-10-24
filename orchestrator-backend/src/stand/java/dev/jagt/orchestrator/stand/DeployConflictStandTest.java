package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("model")
class DeployConflictStandTest {

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("runs")
    @Execution(ExecutionMode.CONCURRENT)
    void aDeployMeetingAnotherCommitStopsAtTheConflictAndIsResolvedOrHandedOver(StandMode mode, int run) throws Exception {
        try (StandRun stand = StandRun.start("deploy-conflict", mode)) {
            stand.store().seedTicket("ABC-4", "Greet the world by default", """
                    Called without an argument, ./greet.sh prints "Hello, " today. It must print exactly \
                    "Hello, World" instead; with an argument it stays "Hello, <name>". Add a check for the \
                    no-argument case to ./check.sh, which must keep passing.""", "To Do");

            StandOutcome outcome = stand.drive(Script.to("ABC-4", "DEPLOYED").with(WorldEvent.deployBranchCommit(
                    "greet.sh", "#!/bin/sh\necho \"Hello there, $1\"\n", "Greet more warmly")));

            assertThat(outcome.status()).as(outcome.describe()).isIn("DEPLOYED", "DEPLOY_CONFLICT");
            assertThat(stand.record().histories().values()).as(outcome.describe()).singleElement()
                    .satisfies(history -> assertThat(history).extracting(RunRecord.Step::status)
                            .contains(TaskStatus.DEPLOY_CONFLICT));
            assertThat(stand.violations()).as(outcome.describe()).isEmpty();
            stand.passed();
        }
    }

    static Stream<Arguments> runs() {
        return StandRuns.of("deploy-conflict");
    }
}
