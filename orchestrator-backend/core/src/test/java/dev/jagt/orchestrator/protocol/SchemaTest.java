package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


import java.util.List;

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
    void holdsAFindingToTheSeveritiesARoundIsJudgedBy() {
        JsonNode schema = mapper.readTree(RoundRead.SCHEMA.json());

        assertThat(schema.path("properties").path("findings").path("items").path("properties").path("severity")
                .path("enum")).extracting(JsonNode::asString)
                .containsExactly("blocking", "wrong", "unproven", "unguarded", "noise");
    }

    @Test
    void holdsACommandMappingToTheWordsTheBoardOffers() {
        JsonNode properties = mapper.readTree(CommandRead.schemaFor(List.of("ship", "do"), List.of("ABC-42", "a1"))
                .json()).path("properties");

        assertThat(properties.path("command").path("enum")).extracting(JsonNode::asString)
                .containsExactly("ship", "do", "none");
        assertThat(properties.path("task").path("enum")).extracting(JsonNode::asString)
                .containsExactly("ABC-42", "a1", "");
    }

    @Test
    void escapesADescriptionCarryingTheBracesOfAnExample() {
        JsonNode schema = mapper.readTree(AgentStatusMessage.SCHEMA.json());

        assertThat(schema.path("properties").path("reviewRequests").path("description").asString())
                .contains("{\"<project>\": \"<url>\"}");
    }
}
