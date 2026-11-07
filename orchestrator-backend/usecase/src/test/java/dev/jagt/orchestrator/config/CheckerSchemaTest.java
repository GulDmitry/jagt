package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CheckerSchemaTest {

  @Test
  void theQaSchemaFromItsDefinitionIsTheOneQaAnsweredBefore() {
    String answer =
        RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")).replies().answer().json();
    String decline =
        RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist"))
            .replies()
            .decline()
            .json();

    assertThat(answer)
        .isEqualTo(
            "{\"type\":\"object\",\"properties\":{\"failure\":{\"type\":\"string\"},"
                + "\"rows\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"criterion\":{\"type\":\"string\"},\"status\":{\"type\":\"string\",\"enum\":[\"met\",\"not"
                + " met\","
                + "\"unprovable\"]},\"evidence\":{\"type\":\"string\"}},\"required\":[\"criterion\",\"status\","
                + "\"evidence\"]}},\"question\":{\"type\":\"string\"},\"waitingOn\":{\"type\":\"string\"}},"
                + "\"required\":[\"failure\"]}");
    assertThat(decline)
        .isEqualTo(
            "{\"type\":\"object\",\"properties\":{\"failure\":{\"type\":\"string\"},"
                + "\"dispute\":{\"type\":\"string\"}},\"required\":[\"failure\"]}");
  }
}
