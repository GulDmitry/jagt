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
class PlanStandTest {

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("runs")
    @Execution(ExecutionMode.CONCURRENT)
    void aTicketOpenedPlanFirstIsPlannedApprovedAndDeployed(StandMode mode, int run) throws Exception {
        try (StandRun stand = StandRun.start("plan", mode)) {
            stand.store().seedTicket("ABC-2", "Shout on request", """
                    ./greet.sh --shout <name> must print the greeting in capitals, "HELLO, ADA" for Ada; without \
                    --shout it stays "Hello, <name>". Add a check for --shout to ./check.sh, which must keep passing.""",
                    "To Do");

            StandOutcome outcome = stand.drive(Script.to("ABC-2", "DEPLOYED").planFirst());

            assertThat(outcome.status()).as(outcome.describe()).isEqualTo("DEPLOYED");
            assertThat(stand.record().histories().values()).as(outcome.describe()).singleElement()
                    .satisfies(history -> assertThat(history).extracting(RunRecord.Step::status)
                            .containsSubsequence(TaskStatus.PLAN_PENDING, TaskStatus.IN_PROGRESS, TaskStatus.DEPLOYED));
            assertThat(stand.project().deployBranchFile("check.sh")).as(outcome.describe()).contains("--shout");
            assertThat(stand.violations()).as(outcome.describe()).isEmpty();
            stand.passed();
        }
    }

    static Stream<Arguments> runs() {
        return StandRuns.of("plan");
    }
}
