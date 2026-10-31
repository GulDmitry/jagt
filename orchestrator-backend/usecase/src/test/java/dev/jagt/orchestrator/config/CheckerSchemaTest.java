package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckerSchemaTest {

    @Test
    void theQaSchemaFromItsDefinitionIsTheOneQaAnsweredBefore() {
        String answer = RoleDefinition.QA.answer().json();
        String decline = RoleDefinition.QA.decline().json();

        assertThat(answer).isEqualTo("{\"type\":\"object\",\"properties\":{\"failure\":{\"type\":\"string\"},"
                + "\"rows\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"criterion\":"
                + "{\"type\":\"string\"},\"status\":{\"type\":\"string\",\"enum\":[\"met\",\"not met\","
                + "\"unprovable\"]},\"evidence\":{\"type\":\"string\"}},\"required\":[\"criterion\",\"status\","
                + "\"evidence\"]}},\"question\":{\"type\":\"string\"},\"waitingOn\":{\"type\":\"string\"}},"
                + "\"required\":[\"failure\"]}");
        assertThat(decline).isEqualTo("{\"type\":\"object\",\"properties\":{\"failure\":{\"type\":\"string\"},"
                + "\"dispute\":{\"type\":\"string\"}},\"required\":[\"failure\"]}");
    }
}
