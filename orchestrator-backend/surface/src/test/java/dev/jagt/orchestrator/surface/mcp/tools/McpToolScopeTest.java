package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.Effect;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.ToolHandler;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.surface.mcp.MessageHandler;
import dev.jagt.orchestrator.surface.mcp.MessageTool;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.OwnStatusReports;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.TaskProvisioning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpToolScopeTest {

    private static final class Registered implements McpToolRegistry {

        private final Map<String, ToolHandler> handlers = new HashMap<>();

        @Override
        public <T extends Message> void tool(String name, Audience audience, Effect effect, Schema schema,
                                             Class<T> message, BiFunction<T, String, MessageContext> context,
                                             MessageHandler<T> handler) {
            handlers.put(name, MessageTool.of(new JsonMapper(), name, audience, message, context, handler));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"deploy_task", "revert_task"})
    void refusesASubAgentReachingForTheToolsThatWriteASharedBranch(String tool) {
        CommandService commands = mock(CommandService.class);
        Registered registered = new Registered();
        new DeployTools(commands).declare(registered);
        ToolHandler handler = registered.handlers.get(tool);

        assertThatThrownBy(() -> handler.call(
                new JsonMapper().readTree("{\"taskId\":\"ABC-1\"}"), "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(tool + " is Master-only");
        verifyNoInteractions(commands);
    }

    @Test
    void offersNoToolThatClosesATask() {
        Registered registered = new Registered();
        new TaskLifecycleTools(mock(TaskProvisioning.class),
                mock(TaskViews.class), mock(ConfigService.class)).declare(registered);
        Map<String, ToolHandler> tools = registered.handlers;

        assertThat(tools).doesNotContainKey("remove_task");
    }

    @Test
    void letsTheMasterDeployBecauseItRunsInNoWorktree() {
        CommandService commands = mock(CommandService.class);
        Registered registered = new Registered();
        new DeployTools(commands).declare(registered);
        ToolHandler handler = registered.handlers.get("deploy_task");
        when(commands.execute("ABC-1", TaskAction.DEPLOY)).thenReturn("deployed ABC-1");

        String answered = handler.call(new JsonMapper().readTree("{\"taskId\":\"ABC-1\"}"), null);

        assertThat(answered).isEqualTo("deployed ABC-1");
    }

    @Test
    void letsTheMasterRevertADeployBecauseItRunsInNoWorktree() {
        CommandService commands = mock(CommandService.class);
        Registered registered = new Registered();
        new DeployTools(commands).declare(registered);
        ToolHandler handler = registered.handlers.get("revert_task");
        when(commands.execute("ABC-1", TaskAction.REVERT)).thenReturn("reverted ABC-1");

        String answered = handler.call(new JsonMapper().readTree("{\"taskId\":\"ABC-1\"}"), null);

        assertThat(answered).isEqualTo("reverted ABC-1");
    }

    @Test
    void refusesAStatusUpdateAimedAtASiblingTask() {
        StateService stateService = mock(StateService.class);
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(stateService.canonicalTaskId("OTHER-1")).thenReturn("OTHER-1");
        OwnStatusReports ownReports = mock(OwnStatusReports.class);
        Registered registered = new Registered();
        new StatusTools(statusReports, ownReports, new CallerScope(stateService)).declare(registered);
        ToolHandler handler = registered.handlers.get("update_agent_status");

        assertThatThrownBy(() -> handler.call(
                new JsonMapper().readTree("{\"status\":\"DONE\",\"taskId\":\"OTHER-1\"}"), "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only act on their own task");
        verifyNoInteractions(statusReports, ownReports);
    }

    @Test
    void letsAnAgentReportOnItsOwnTaskWithoutNamingIt() {
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        OwnStatusReports ownReports = mock(OwnStatusReports.class);
        when(ownReports.contextFor(any())).thenReturn(MessageContext.NONE);
        Registered registered = new Registered();
        new StatusTools(statusReports, ownReports, new CallerScope(mock(StateService.class))).declare(registered);
        ToolHandler handler = registered.handlers.get("update_agent_status");
        when(ownReports.report(argThat(said -> "IN_PROGRESS".equals(said.status())), eq("MINE-1")))
                .thenReturn("recorded for MINE-1");

        String answered = handler.call(
                new JsonMapper().readTree("{\"status\":\"IN_PROGRESS\",\"message\":\"working\",\"why\":\"started on the ticket\"}"),
                "MINE-1");

        assertThat(answered).isEqualTo("recorded for MINE-1");
    }

    @Test
    void notifiesTheHumanWithTheTitleAndLineTheCallerGave() {
        AgentStatusReports statusReports = mock(AgentStatusReports.class);
        when(statusReports.notifyUser("jagt", "round addressed")).thenReturn("notified");
        Registered registered = new Registered();
        new StatusTools(statusReports, mock(OwnStatusReports.class), new CallerScope(mock(StateService.class)))
                .declare(registered);
        ToolHandler handler = registered.handlers.get("notify_user");

        String answered = handler.call(new JsonMapper().readTree("{\"title\":\"jagt\",\"message\":\"round addressed\"}"),
                "MINE-1");

        assertThat(answered).isEqualTo("notified");
    }

    @Test
    void refusesASubAgentCreatingATask() {
        TaskProvisioning provisioning = mock(TaskProvisioning.class);
        Registered registered = new Registered();
        new TaskLifecycleTools(provisioning, mock(TaskViews.class),
                mock(ConfigService.class)).declare(registered);
        ToolHandler handler = registered.handlers.get("initialize_task");

        assertThatThrownBy(() -> handler.call(
                new JsonMapper().readTree("{\"taskId\":\"ABC-2\",\"projectKey\":\"demo\"}"), "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialize_task is Master-only");
        verifyNoInteractions(provisioning);
    }

    @Test
    void tellsTheMasterWhatEachConfiguredProjectIsAndWhichLabelsPlaceAnItemInIt() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of("api", new ProjectConfig("/api",
                "origin/main", "dev", List.of("backend"), List.of(), "the order API", null))));
        Registered registered = new Registered();
        new TaskLifecycleTools(mock(TaskProvisioning.class),
                mock(TaskViews.class), config).declare(registered);
        ToolHandler handler = registered.handlers.get("list_projects");

        Object listed = handler.call(new JsonMapper().readTree("{}"), null);

        assertThat(listed.toString()).contains("api: the order API — labels [backend]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"write_task_context", "open_task_tab", "close_task_tab", "focus_task"})
    void refusesASubAgentDrivingEvenItsOwnSession(String tool) {
        AgentSessions sessions = mock(AgentSessions.class);
        Registered registered = new Registered();
        new SessionTools(sessions, mock(CommandService.class)).declare(registered);
        ToolHandler handler = registered.handlers.get(tool);

        assertThatThrownBy(() -> handler.call(
                new JsonMapper().readTree("{\"taskId\":\"MINE-1\",\"instructions\":\"commit and push\"}"), "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(tool + " is Master-only");
        verifyNoInteractions(sessions);
    }

    @ParameterizedTest
    @CsvSource({"diff, DIFF", "project, IDE"})
    void opensAnAgentsOwnTaskInTheIdeThroughTheCommandGate(String mode, TaskAction action) {
        CommandService commands = mock(CommandService.class);
        Registered registered = new Registered();
        new IdeTools(commands, new CallerScope(mock(StateService.class))).declare(registered);
        ToolHandler handler = registered.handlers.get("open_in_ide");
        when(commands.execute("MINE-1", action)).thenReturn("opened MINE-1");

        String answered = handler.call(new JsonMapper().readTree("{\"mode\":\"" + mode + "\"}"), "MINE-1");

        assertThat(answered).isEqualTo("opened MINE-1");
    }

    @Test
    void focusesATaskThroughTheCommandGate() {
        AgentSessions sessions = mock(AgentSessions.class);
        CommandService commands = mock(CommandService.class);
        Registered registered = new Registered();
        new SessionTools(sessions, commands).declare(registered);
        ToolHandler handler = registered.handlers.get("focus_task");
        when(commands.execute("ABC-1", TaskAction.FOCUS)).thenReturn("focused ABC-1");

        String answered = handler.call(new JsonMapper().readTree("{\"taskId\":\"ABC-1\"}"), null);

        assertThat(answered).isEqualTo("focused ABC-1");
        verifyNoInteractions(sessions);
    }

    @Test
    void opensATaskTabInTheModeTheMasterNamed() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.openTaskTab("ABC-1", "plan")).thenReturn("tab open for ABC-1");
        Registered registered = new Registered();
        new SessionTools(sessions, mock(CommandService.class)).declare(registered);
        ToolHandler handler = registered.handlers.get("open_task_tab");

        String answered = handler.call(new JsonMapper().readTree("{\"taskId\":\"ABC-1\",\"mode\":\"plan\"}"), null);

        assertThat(answered).isEqualTo("tab open for ABC-1");
    }

    @Test
    void closesATaskTabForTheMaster() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.closeTaskTab("ABC-1")).thenReturn("tab closed for ABC-1");
        Registered registered = new Registered();
        new SessionTools(sessions, mock(CommandService.class)).declare(registered);
        ToolHandler handler = registered.handlers.get("close_task_tab");

        String answered = handler.call(new JsonMapper().readTree("{\"taskId\":\"ABC-1\"}"), null);

        assertThat(answered).isEqualTo("tab closed for ABC-1");
    }

    @Test
    void writesTheInstructionsTheMasterGaveToTheTasksContext() {
        AgentSessions sessions = mock(AgentSessions.class);
        when(sessions.writeTaskContext("ABC-1", "commit and push")).thenReturn("context written for ABC-1");
        Registered registered = new Registered();
        new SessionTools(sessions, mock(CommandService.class)).declare(registered);
        ToolHandler handler = registered.handlers.get("write_task_context");

        String answered = handler.call(
                new JsonMapper().readTree("{\"taskId\":\"ABC-1\",\"instructions\":\"commit and push\"}"), null);

        assertThat(answered).isEqualTo("context written for ABC-1");
    }

    @Test
    void listsEveryTaskForTheMaster() {
        TaskViews taskViews = mock(TaskViews.class);
        when(taskViews.listing()).thenReturn("ABC-1 IN_PROGRESS");
        Registered registered = new Registered();
        new TaskLifecycleTools(mock(TaskProvisioning.class), taskViews, mock(ConfigService.class))
                .declare(registered);
        ToolHandler handler = registered.handlers.get("list_tasks");

        String answered = handler.call(new JsonMapper().readTree("{}"), null);

        assertThat(answered).isEqualTo("ABC-1 IN_PROGRESS");
    }

    @Test
    void initializesATaskForTheMasterAndAnswersWhatProvisioningSaid() {
        TaskProvisioning provisioning = mock(TaskProvisioning.class);
        when(provisioning.initializeTask(any())).thenReturn("ABC-2 initialized");
        Registered registered = new Registered();
        new TaskLifecycleTools(provisioning, mock(TaskViews.class), mock(ConfigService.class)).declare(registered);
        ToolHandler handler = registered.handlers.get("initialize_task");

        String answered = handler.call(
                new JsonMapper().readTree("{\"taskId\":\"ABC-2\",\"projectKey\":\"demo\",\"why\":\"user asked for ABC-2\"}"), null);

        assertThat(answered).isEqualTo("ABC-2 initialized");
    }

    @Test
    void refusesASubAgentListingEveryTask() {
        TaskViews taskViews = mock(TaskViews.class);
        Registered registered = new Registered();
        new TaskLifecycleTools(mock(TaskProvisioning.class),
                taskViews, mock(ConfigService.class)).declare(registered);
        ToolHandler handler = registered.handlers.get("list_tasks");

        assertThatThrownBy(() -> handler.call(
                new JsonMapper().readTree("{}"), "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("list_tasks is Master-only");
        verifyNoInteractions(taskViews);
    }
}
