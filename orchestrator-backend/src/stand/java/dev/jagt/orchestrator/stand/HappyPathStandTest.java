package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

@Tag("model")
class HappyPathStandTest {

    @Test
    void aTicketTheMasterCarriesAloneLandsOnTheDeployBranchThroughOneMergeRequest() throws Exception {
        try (StandRun run = StandRun.start("happy-path")) {
            run.store().seedTicket("ABC-1", "Greet the world by default", """
                    Called without an argument, ./greet.sh prints "Hello, " today. It must print exactly \
                    "Hello, World" instead; with an argument it stays "Hello, <name>". Add a check for the \
                    no-argument case to ./check.sh, which must keep passing.""", "To Do");

            StandOutcome outcome = run.drive("ABC-1", "DEPLOYED", Duration.ofMinutes(30), Duration.ofSeconds(90));

            assertThat(outcome.status()).as(outcome.describe()).isEqualTo("DEPLOYED");
            List<JsonNode> requests = run.store().allRequests();
            assertThat(requests).as(outcome.describe()).hasSize(1);
            assertThat(run.project().onDeployBranch(requests.get(0).path("sourceBranch").asString()))
                    .as(outcome.describe()).isTrue();
            assertThat(run.project().deployBranchFile("check.sh")).as(outcome.describe()).contains("Hello, World");
            run.passed();
        }
    }
}
