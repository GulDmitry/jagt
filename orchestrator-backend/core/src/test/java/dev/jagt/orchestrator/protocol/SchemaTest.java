package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void offersEveryStatusTheMachineHasAndNoOtherValue() {
        JsonNode schema = mapper.readTree(AgentStatusMessage.SCHEMA.json());

        assertThat(schema.path("properties").path("status").path("enum"))
                .extracting(JsonNode::asString)
                .containsExactlyElementsOf(List.of(TaskStatus.values()).stream().map(Enum::name).toList());
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
