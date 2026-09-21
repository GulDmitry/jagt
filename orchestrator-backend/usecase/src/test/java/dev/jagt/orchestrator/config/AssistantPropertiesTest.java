package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.AssistantCallKind;
import org.junit.jupiter.api.Test;

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
}
