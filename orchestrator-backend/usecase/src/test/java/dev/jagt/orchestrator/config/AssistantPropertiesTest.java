package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.AssistantCallKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPropertiesTest {

    @Test
    void givesAKindItsOwnServersWhereItDeclaresThem() {
        AssistantProperties assistant = AssistantProperties.empty()
                .withMcpConfig("{\"mcpServers\":{\"everything\":{}}}")
                .withMcpConfigByKind(Map.of(AssistantCallKind.TICKET_READ, "{\"mcpServers\":{\"tracker\":{}}}"));

        assertThat(assistant.mcpConfigFor(AssistantCallKind.TICKET_READ))
                .isEqualTo("{\"mcpServers\":{\"tracker\":{}}}");
    }

    @Test
    void fallsBackToThePinnedListForAKindThatDeclaresNone() {
        AssistantProperties assistant = AssistantProperties.empty()
                .withMcpConfig("{\"mcpServers\":{\"everything\":{}}}")
                .withMcpConfigByKind(Map.of(AssistantCallKind.TICKET_READ, "{\"mcpServers\":{\"tracker\":{}}}"));

        assertThat(assistant.mcpConfigFor(AssistantCallKind.REVIEW_SWEEP))
                .isEqualTo("{\"mcpServers\":{\"everything\":{}}}");
    }

    @Test
    void inheritsTheHumansOwnConfigurationWhereNothingIsPinnedAtAll() {
        assertThat(AssistantProperties.empty().mcpConfigFor(AssistantCallKind.TICKET_READ)).isEmpty();
    }

    @Test
    void ceilsASpendAt70CentsWhereNoneIsSet() {
        assertThat(AssistantProperties.empty().maxCostUsd()).isEqualTo(5.00);
    }

    @Test
    void readsTheUserProjectAndLocalSettingsWhereNoSourceIsNamed() {
        assertThat(new AssistantProperties("  ", null, null, null, null, 0).settingSources())
                .isEqualTo("user,project,local");
    }

    @Test
    void keepsTheSettingSourcesTheHumanNamed() {
        assertThat(new AssistantProperties("user", null, null, null, null, 0).settingSources()).isEqualTo("user");
    }

    @Test
    void allowsNoToolWhereNoneIsListedAndKeepsTheOnesThatAre() {
        assertThat(new AssistantProperties(null, null, null, null, null, 0).allowedTools()).isEmpty();
        assertThat(new AssistantProperties(null, null, List.of("Read"), null, null, 0).allowedTools())
                .containsExactly("Read");
    }
}
