package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


import static org.assertj.core.api.Assertions.assertThat;

class SchemaTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void offersOnlyTheStatusesASessionMayReport() {
        JsonNode schema = mapper.readTree(AgentStatusMessage.SCHEMA.json());

        assertThat(schema.path("properties").path("status").path("enum"))
                .extracting(JsonNode::asString)
                .containsExactly("PLAN_PENDING", "IN_PROGRESS", "REVIEW_PENDING", "SHIPPING", "CI_POLLING", "CI_FAILED");
    }

    @Test
    void asksForTheOneFieldWithoutWhichAReportSaysNothing() {
        JsonNode schema = mapper.readTree(AgentStatusMessage.SCHEMA.json());

        assertThat(schema.path("required")).extracting(JsonNode::asString).containsExactly("status");
    }

    @Test
    void offersTheSameOutcomesTheRulesJudge() {
        JsonNode schema = mapper.readTree(AgentStatusMessage.SCHEMA.json());

        assertThat(schema.path("properties").path("outcome").path("enum"))
                .extracting(JsonNode::asString)
                .containsExactlyInAnyOrderElementsOf(AgentStatusMessage.OUTCOMES);
    }

    @Test
    void escapesADescriptionCarryingTheBracesOfAnExample() {
        JsonNode schema = mapper.readTree(AgentStatusMessage.SCHEMA.json());

        assertThat(schema.path("properties").path("reviewRequests").path("description").asString())
                .contains("{\"<project>\": \"<url>\"}");
    }
}
