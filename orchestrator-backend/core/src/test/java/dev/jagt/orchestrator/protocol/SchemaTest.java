package dev.jagt.orchestrator.protocol;

import java.util.Map;
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
    void asksForTheStatusAndTheReasonForIt() {
        JsonNode schema = mapper.readTree(AgentStatusMessage.SCHEMA.json());

        assertThat(schema.path("required")).extracting(JsonNode::asString).containsExactly("status", "why");
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

    @Test
    void rendersTextRequiredAndChoiceFieldsWithTheirDescriptions() {
        String json = Schema.of("What it does").text("note", "Free words").required("count", "integer", null)
                .choiceRequired("kind", List.of("a", "b"), "One of them").choice("mood", List.of("calm"), null)
                .json();

        assertThat(json).isEqualTo("{\"description\":\"What it does\",\"type\":\"object\",\"properties\":{"
                + "\"note\":{\"type\":\"string\",\"description\":\"Free words\"},"
                + "\"count\":{\"type\":\"integer\"},"
                + "\"kind\":{\"type\":\"string\",\"description\":\"One of them\",\"enum\":[\"a\",\"b\"]},"
                + "\"mood\":{\"type\":\"string\",\"enum\":[\"calm\"]}},\"required\":[\"count\",\"kind\"]}");
    }

    @Test
    void rendersAListOfStringsAsOptionalOrRequired() {
        String json = Schema.answer().texts("tags", null).requiredTexts("labels", null).json();

        assertThat(json).isEqualTo("{\"type\":\"object\",\"properties\":{"
                + "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"labels\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"labels\"]}");
    }

    @Test
    void rendersEveryFieldOfARecordAsRequiredAndOnlyTheChosenOnesAsAnEnum() {
        String json = Schema.answer().records("findings", null, List.of("file", "severity"),
                Map.of("severity", List.of("low", "high"))).json();

        assertThat(json).isEqualTo("{\"type\":\"object\",\"properties\":{\"findings\":{\"type\":\"array\","
                + "\"items\":{\"type\":\"object\",\"properties\":{\"file\":{\"type\":\"string\"},"
                + "\"severity\":{\"type\":\"string\",\"enum\":[\"low\",\"high\"]}},"
                + "\"required\":[\"file\",\"severity\"]}}},\"required\":[]}");
    }

    @Test
    void rendersAStringToStringObjectWithItsExamplesOnlyWhereThereAreSome() {
        String shown = Schema.answer().pairs("urls", null, List.of(Map.of("api", "https://host/1"))).json();
        String bare = Schema.answer().pairs("urls", null, List.of()).json();

        assertThat(shown).isEqualTo("{\"type\":\"object\",\"properties\":{\"urls\":{\"type\":\"object\","
                + "\"additionalProperties\":{\"type\":\"string\"},\"examples\":[{\"api\":\"https://host/1\"}]}},"
                + "\"required\":[]}");
        assertThat(bare).doesNotContain("examples");
    }

    @Test
    void constrainsAProjectAnswerToTheConfiguredProjectsPlusNone() {
        JsonNode schema = mapper.readTree(ProjectRead.schemaFor(List.of("api", "web")).json());

        assertThat(schema.path("properties").path("project").path("enum")).extracting(JsonNode::asString)
                .containsExactly("api", "web", "none");
        assertThat(schema.path("required")).extracting(JsonNode::asString)
                .containsExactly("failure", "project", "reason", "rule");
    }

    @Test
    void constrainsARuleAnswerToTheHeldRulesPlusNone() {
        JsonNode single = mapper.readTree(RuleRead.schemaFor(List.of("labels carry it")).json());
        JsonNode pair = mapper.readTree(RuleRead.pairSchemaFor(List.of("labels carry it")).json());

        assertThat(single.path("properties").path("rule").path("enum")).extracting(JsonNode::asString)
                .containsExactly("labels carry it", "none");
        assertThat(pair.path("properties").path("kept").path("enum")).extracting(JsonNode::asString)
                .containsExactly("labels carry it", "none");
        assertThat(pair.path("properties").path("duplicate").path("enum")).extracting(JsonNode::asString)
                .containsExactly("labels carry it", "none");
    }

    @Test
    void asksATaskToolForTheTaskAndAToolWithNoArgumentsForNothing() {
        JsonNode task = mapper.readTree(TaskRef.schema("Do it").json());
        JsonNode nothing = mapper.readTree(NoArguments.schema("Just do it").json());

        assertThat(task.path("required")).extracting(JsonNode::asString).containsExactly("taskId");
        assertThat(nothing.path("description").asString()).isEqualTo("Just do it");
        assertThat(nothing.path("required")).isEmpty();
    }
}
