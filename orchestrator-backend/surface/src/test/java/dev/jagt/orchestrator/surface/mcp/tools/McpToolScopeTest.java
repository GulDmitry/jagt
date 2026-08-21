package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.ToolHandler;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.capability.done.TaskRetirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The wiring between a tool and the scope rule, which is what an agent actually reaches. Testing
 * {@code CallerScope} alone leaves a tool free to skip it, and these are the tools that write outside a
 * worktree.
 */
class McpToolScopeTest {

    private final StateService stateService = mock(StateService.class);
    private final CallerScope scope = new CallerScope(stateService);
    private final CommandService commands = mock(CommandService.class);
    private final TaskRetirement retirement = mock(TaskRetirement.class);
    private final AgentStatusReports statusReports = mock(AgentStatusReports.class);

    private static Map<String, ToolHandler> declared(McpTools group) {
        Map<String, ToolHandler> handlers = new HashMap<>();
        McpToolRegistry registry = (name, schema, handler) -> handlers.put(name, handler);
        group.declare(registry);
        return handlers;
    }

    private static JsonNode args(String json) {
        return new JsonMapper().readTree(json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deploy_task", "revert_task"})
    void refusesASubAgentReachingForTheToolsThatWriteASharedBranch(String tool) {
        ToolHandler handler = declared(new DeployTools(commands, scope)).get(tool);

        assertThatThrownBy(() -> handler.call(args("{\"taskId\":\"ABC-1\"}"), "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(tool + " is Master-only");
        verifyNoInteractions(commands);
    }

    @Test
    void refusesASubAgentRetiringATask() {
        ToolHandler handler = declared(new TaskLifecycleTools(mock(TaskProvisioning.class), retirement,
                stateService, scope)).get("remove_task");

        assertThatThrownBy(() -> handler.call(args("{\"taskId\":\"ABC-1\"}"), "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remove_task is Master-only");
        verifyNoInteractions(retirement);
    }

    @Test
    void refusesAStatusUpdateAimedAtASiblingTask() {
        when(stateService.canonicalTaskId("OTHER-1")).thenReturn("OTHER-1");
        ToolHandler handler = declared(new StatusTools(statusReports, scope)).get("update_agent_status");

        assertThatThrownBy(() -> handler.call(args("{\"status\":\"DONE\",\"taskId\":\"OTHER-1\"}"), "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only act on their own task");
        verifyNoInteractions(statusReports);
    }

    @Test
    void letsAnAgentReportOnItsOwnTaskWithoutNamingIt() {
        ToolHandler handler = declared(new StatusTools(statusReports, scope)).get("update_agent_status");

        handler.call(args("{\"status\":\"IN_PROGRESS\",\"message\":\"working\"}"), "MINE-1");

        verify(statusReports).report("IN_PROGRESS", "working", null, null, "MINE-1");
    }

    @Test
    void refusesASubAgentCreatingATask() {
        TaskProvisioning provisioning = mock(TaskProvisioning.class);
        ToolHandler handler = declared(new TaskLifecycleTools(provisioning, retirement, stateService, scope))
                .get("initialize_task");

        assertThatThrownBy(() -> handler.call(args("{\"taskId\":\"ABC-2\",\"projectKey\":\"demo\"}"), "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialize_task is Master-only");
        verifyNoInteractions(provisioning);
    }

    /** A sibling's task_context.md is the instructions its agent acts on, so writing it is a hijack. */
    @ParameterizedTest
    @ValueSource(strings = {"write_task_context", "open_task_tab", "focus_task"})
    void refusesASubAgentReachingIntoASiblingsSession(String tool) {
        when(stateService.canonicalTaskId("OTHER-1")).thenReturn("OTHER-1");
        AgentSessions sessions = mock(AgentSessions.class);
        ToolHandler handler = declared(new SessionTools(sessions, scope)).get(tool);

        assertThatThrownBy(() -> handler.call(
                args("{\"taskId\":\"OTHER-1\",\"instructions\":\"do this instead\"}"), "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only act on their own task");
        verifyNoInteractions(sessions);
    }
}
