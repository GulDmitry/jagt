package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ToolSchemasTest {

  @Test
  void toolListsItsRequiredAndOptionalPropertiesWithTheirTypes() {
    JsonNode tool = ToolSchemas.tool("t", "d", "id:integer,name:string", "note:string");

    JsonNode schema = tool.path("inputSchema");
    assertThat(schema.path("properties").path("id").path("type").asString()).isEqualTo("integer");
    assertThat(schema.path("properties").path("note").path("type").asString()).isEqualTo("string");
    assertThat(schema.path("required"))
        .extracting(JsonNode::asString)
        .containsExactly("id", "name");
  }

  @Test
  void toolWithNoPropertiesHasNoRequiredNames() {
    JsonNode tool = ToolSchemas.tool("t", "d", "", "");

    assertThat(tool.path("inputSchema").path("required")).isEmpty();
    assertThat(tool.path("inputSchema").path("properties")).isEmpty();
  }
}
