package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

@Tag("model")
class HappyPathStandTest {

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("runs")
    @Execution(ExecutionMode.CONCURRENT)
    void aTicketLandsOnTheDeployBranchThroughOneMergeRequest(StandMode mode, int run) throws Exception {
        try (StandRun stand = StandRun.start("happy-path", mode)) {
            stand.store().seedTicket("ABC-1", "Greet the world by default", """
                    Called without an argument, ./greet.sh prints "Hello, " today. It must print exactly \
                    "Hello, World" instead; with an argument it stays "Hello, <name>". Add a check for the \
                    no-argument case to ./check.sh, which must keep passing.""", "To Do");

            StandOutcome outcome = stand.drive(Script.to("ABC-1", "DEPLOYED"));

            assertThat(outcome.status()).as(outcome.describe()).isEqualTo("DEPLOYED");
            List<JsonNode> requests = stand.store().allRequests();
            assertThat(requests).as(outcome.describe()).hasSize(1);
            assertThat(stand.project().onDeployBranch(requests.get(0).path("sourceBranch").asString()))
                    .as(outcome.describe()).isTrue();
            assertThat(stand.project().deployBranchFile("check.sh")).as(outcome.describe()).contains("Hello, World");
            assertThat(stand.violations()).as(outcome.describe()).isEmpty();
            stand.passed();
        }
    }

    static Stream<Arguments> runs() {
        return StandRuns.of("happy-path");
    }
}
