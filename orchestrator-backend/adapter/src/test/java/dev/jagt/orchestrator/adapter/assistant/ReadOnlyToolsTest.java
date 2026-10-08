package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.task.AssistantCallKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadOnlyToolsTest {

    @Test
    void allowsOnlyTheReadShapedToolsOfEveryServerTheHumanLists() {
        McpHealth mcp = mock(McpHealth.class);
        when(mcp.servers()).thenReturn(Optional.of(List.of("plugin:acme:tracker")));

        List<String> allowed = new ReadOnlyTools(mcp, AssistantProperties.empty()).allowed(AssistantCallKind.MR_READ);

        assertThat(allowed).contains("mcp__plugin_acme_tracker__get*", "mcp__plugin_acme_tracker__search*")
                .doesNotContain("mcp__plugin_acme_tracker", "mcp__plugin_acme_tracker__*");
    }

    @Test
    void allowsTheReadsOfTheDeclaredServersWhenTheServersArePinned() {
        var pinned = AssistantProperties.empty().withMcpConfig("{\"mcpServers\":{\"gitlab\":{\"command\":\"x\"}}}");

        List<String> allowed = new ReadOnlyTools(mock(McpHealth.class), pinned).allowed(AssistantCallKind.MR_READ);

        assertThat(allowed).contains("mcp__gitlab__list*");
    }

    @Test
    void widensTheReadsWithTheHumansOwnListWhenOneIsNamed() {
        McpHealth mcp = mock(McpHealth.class);
        when(mcp.servers()).thenReturn(Optional.of(List.of("gitlab")));
        var named = AssistantProperties.empty().withAllowedTools(List.of("mcp__acme_jira"));

        List<String> allowed = new ReadOnlyTools(mcp, named).allowed(AssistantCallKind.TICKET_READ);

        assertThat(allowed).contains("mcp__acme_jira", "mcp__gitlab__get*");
    }
}
