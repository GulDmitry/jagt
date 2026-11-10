package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AssistantCallKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

  @Test
  void givesKindItsOwnServersWhereItDeclaresThem() {
    AssistantProperties assistant =
        AssistantProperties.empty()
            .withMcpConfig("{\"mcpServers\":{\"everything\":{}}}")
            .withMcpConfigByKind(
                Map.of(AssistantCallKind.TICKET_READ, "{\"mcpServers\":{\"tracker\":{}}}"));

    assertThat(assistant.mcpConfigFor(AssistantCallKind.TICKET_READ))
        .isEqualTo("{\"mcpServers\":{\"tracker\":{}}}");
  }

  @Test
  void fallsBackToThePinnedListForKindThatDeclaresNone() {
    AssistantProperties assistant =
        AssistantProperties.empty()
            .withMcpConfig("{\"mcpServers\":{\"everything\":{}}}")
            .withMcpConfigByKind(
                Map.of(AssistantCallKind.TICKET_READ, "{\"mcpServers\":{\"tracker\":{}}}"));

    assertThat(assistant.mcpConfigFor(AssistantCallKind.REVIEW_SWEEP))
        .isEqualTo("{\"mcpServers\":{\"everything\":{}}}");
  }

  @Test
  void inheritsTheHumansOwnConfigurationWhereNothingIsPinnedAtAll() {
    assertThat(AssistantProperties.empty().mcpConfigFor(AssistantCallKind.TICKET_READ)).isEmpty();
  }

  @Test
  void ceilsSpendAt1Dollar75WhereNoneIsSet() {
    assertThat(AssistantProperties.empty().maxCostUsd()).isEqualTo(2.55);
  }

  @Test
  void readsTheUserProjectAndLocalSettingsWhereNoSourceIsNamed() {
    assertThat(AssistantProperties.empty().withSettingSources("  ").settingSources())
        .isEqualTo("user,project,local");
  }

  @Test
  void keepsTheSettingSourcesTheHumanNamed() {
    assertThat(AssistantProperties.empty().withSettingSources("user").settingSources())
        .isEqualTo("user");
  }

  @Test
  void allowsNoToolWhereNoneIsListedAndKeepsTheOnesThatAre() {
    assertThat(AssistantProperties.empty().allowedTools()).isEmpty();
    assertThat(AssistantProperties.empty().withAllowedTools(List.of("Read")).allowedTools())
        .containsExactly("Read");
  }
}
