package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
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

        List<String> allowed = new ReadOnlyTools(mcp, AssistantProperties.empty(), new ReadScopes(), null).allowed(AssistantCallKind.MR_READ);

        assertThat(allowed).contains("mcp__plugin_acme_tracker__get*", "mcp__plugin_acme_tracker__search*")
                .doesNotContain("mcp__plugin_acme_tracker", "mcp__plugin_acme_tracker__*");
    }

    @Test
    void allowsTheReadsOfTheDeclaredServersWhenTheServersArePinned() {
        var pinned = AssistantProperties.empty().withMcpConfig("{\"mcpServers\":{\"gitlab\":{\"command\":\"x\"}}}");

        List<String> allowed = new ReadOnlyTools(mock(McpHealth.class), pinned, new ReadScopes(), null).allowed(AssistantCallKind.MR_READ);

        assertThat(allowed).contains("mcp__gitlab__list*");
    }

    @Test
    void widensTheReadsWithTheHumansOwnListWhenOneIsNamed() {
        McpHealth mcp = mock(McpHealth.class);
        when(mcp.servers()).thenReturn(Optional.of(List.of("gitlab")));
        var named = AssistantProperties.empty().withAllowedTools(List.of("mcp__acme_jira"));

        List<String> allowed = new ReadOnlyTools(mcp, named, new ReadScopes(), null).allowed(AssistantCallKind.TICKET_READ);

        assertThat(allowed).contains("mcp__acme_jira", "mcp__gitlab__get*");
    }

    @Test
    void refusesAWriteNamedByItsVerbMidNameToo() {
        assertThat(ReadOnlyTools.MCP_WRITES).contains("mcp__*__new*", "mcp__*__hover*", "mcp__*__take*",
                "mcp__*__*_write_*", "mcp__*__*_clear", "mcp__*__*_delete", "mcp__*__*_replace");
    }

    @Test
    void putsEveryCallOfAFencedRunToJagtAndRefusesItWhenJagtCannotAnswer() {
        ReadScopes scopes = new ReadScopes();
        ReadScope scope = new ReadScope(List.of(), List.of("StructuredOutput"), false);

        List<String> settings = new ReadOnlyTools(mock(McpHealth.class), AssistantProperties.empty(), scopes,
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"))
                .fence("f1", scope);

        assertThat(settings.getFirst()).isEqualTo("--settings");
        assertThat(settings.get(1)).contains("PreToolUse", "http://127.0.0.1:8290/api/agent/read/f1", "exit 2");
        assertThat(scopes.find("f1")).contains(scope);
    }

    @Test
    void liftsTheFenceSoNoLaterCallPassesUnderIt() {
        ReadScopes scopes = new ReadScopes();
        ReadOnlyTools tools = new ReadOnlyTools(mock(McpHealth.class), AssistantProperties.empty(), scopes,
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
        tools.fence("f1", new ReadScope(List.of(), List.of(), false));

        tools.lift("f1");

        assertThat(scopes.find("f1")).isEmpty();
    }
}
