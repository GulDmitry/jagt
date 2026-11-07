package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.AssistantCallKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

  @Test
  void givesAKindItsOwnServersWhereItDeclaresThem() {
    AssistantProperties assistant =
        AssistantProperties.empty()
            .withMcpConfig("{\"mcpServers\":{\"everything\":{}}}")
            .withMcpConfigByKind(
                Map.of(AssistantCallKind.TICKET_READ, "{\"mcpServers\":{\"tracker\":{}}}"));

    assertThat(assistant.mcpConfigFor(AssistantCallKind.TICKET_READ))
        .isEqualTo("{\"mcpServers\":{\"tracker\":{}}}");
  }

  @Test
  void fallsBackToThePinnedListForAKindThatDeclaresNone() {
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
  void ceilsASpendAt1Dollar75WhereNoneIsSet() {
    assertThat(AssistantProperties.empty().maxCostUsd()).isEqualTo(2.55);
  }

  @Test
  void readsTheUserProjectAndLocalSettingsWhereNoSourceIsNamed() {
    assertThat(
            new AssistantProperties("  ", null, null, null, null, 0, 0, java.time.Duration.ZERO)
                .settingSources())
        .isEqualTo("user,project,local");
  }

  @Test
  void keepsTheSettingSourcesTheHumanNamed() {
    assertThat(
            new AssistantProperties("user", null, null, null, null, 0, 0, java.time.Duration.ZERO)
                .settingSources())
        .isEqualTo("user");
  }

  @Test
  void allowsNoToolWhereNoneIsListedAndKeepsTheOnesThatAre() {
    assertThat(
            new AssistantProperties(null, null, null, null, null, 0, 0, java.time.Duration.ZERO)
                .allowedTools())
        .isEmpty();
    assertThat(
            new AssistantProperties(
                    null, null, List.of("Read"), null, null, 0, 0, java.time.Duration.ZERO)
                .allowedTools())
        .containsExactly("Read");
  }
}
