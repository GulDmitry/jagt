package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.task.AssistantCallKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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
                .doesNotContain("mcp__plugin_acme_tracker", "mcp__plugin_acme_tracker__*",
                        "mcp__plugin_acme_tracker__query*");
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
        var named = AssistantProperties.empty().withAllowedTools(List.of("mcp__acme_jira__add_comment"));

        List<String> allowed = new ReadOnlyTools(mcp, named, new ReadScopes(), null).allowed(AssistantCallKind.TICKET_READ);

        assertThat(allowed).contains("mcp__acme_jira__add_comment", "mcp__gitlab__get*");
    }

    @Test
    void grantsABareServerTheHumanNamesOnlyItsReads() {
        var named = AssistantProperties.empty().withAllowedTools(List.of("mcp__your-jira-mcp"));

        List<String> allowed = new ReadOnlyTools(mock(McpHealth.class), named, new ReadScopes(), null)
                .allowed(AssistantCallKind.TICKET_READ);

        assertThat(allowed).contains("mcp__your-jira-mcp__get*", "mcp__your-jira-mcp__search*")
                .doesNotContain("mcp__your-jira-mcp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mcp__plugin_omc_t__state_clear", "mcp__plugin_omc_t__notepad_write_manual",
            "mcp__plugin_omc_t__lsp_rename", "mcp__plugin_acme_browser__new_page", "mcp__gitlab__save_note",
            "mcp__x__getAndDeleteIssue", "mcp__x__getOrCreateSession", "mcp__x__get-delete"})
    void refusesAWriteNamedByItsVerbMidNameToo(String tool) {
        assertThat(ReadOnlyTools.MCP_WRITES)
                .anyMatch(glob -> Pattern.compile(Pattern.quote(glob).replace("*", "\\E.*\\Q")).matcher(tool).matches());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mcp__gitlab__get_merge_request", "mcp__plugin_omc_t__lsp_hover",
            "mcp__docs__get_type_info", "mcp__atlassian__getJiraIssue", "mcp__atlassian__getTransitionsForJiraIssue",
            "mcp__atlassian__searchJiraIssuesUsingJql", "mcp__atlassian__getConfluencePageFooterComments"})
    void letsAReadWhoseNameHoldsAWriteVerbThrough(String tool) {
        assertThat(ReadOnlyTools.MCP_WRITES)
                .noneMatch(glob -> Pattern.compile(Pattern.quote(glob).replace("*", "\\E.*\\Q")).matcher(tool).matches());
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
        assertThat(scopes.ask("f1")).contains(scope.withWrites(ReadOnlyTools.MCP_WRITES));
    }

    @Test
    void saysAFenceNoCallReachedHeldNothing() {
        ReadOnlyTools tools = new ReadOnlyTools(mock(McpHealth.class), AssistantProperties.empty(), new ReadScopes(),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
        tools.fence("f1", new ReadScope(List.of(), List.of(), false));

        assertThat(tools.lift("f1")).contains("no call reached the read gate");
    }

    @Test
    void liftsTheFenceSoNoLaterCallPassesUnderIt() {
        ReadScopes scopes = new ReadScopes();
        ReadOnlyTools tools = new ReadOnlyTools(mock(McpHealth.class), AssistantProperties.empty(), scopes,
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
        tools.fence("f1", new ReadScope(List.of(), List.of(), false));

        tools.lift("f1");

        assertThat(scopes.ask("f1")).isEmpty();
    }
}
