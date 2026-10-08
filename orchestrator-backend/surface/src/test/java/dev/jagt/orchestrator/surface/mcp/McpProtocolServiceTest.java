package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.surface.mcp.tools.DeployTools;
import dev.jagt.orchestrator.surface.mcp.tools.SessionTools;
import dev.jagt.orchestrator.surface.mcp.tools.StatusTools;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.NoArguments;
import dev.jagt.orchestrator.service.StateService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpProtocolServiceTest {

    private static List<McpTools> groups() {
        CallerScope scope = new CallerScope(mock(StateService.class));
        return List.of(new StatusTools(mock(AgentStatusReports.class), scope),
                new SessionTools(mock(AgentSessions.class), mock(CommandService.class)));
    }

    @Test
    void offersASessionOnlyTheStatusesItMayReport(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, state, groups());

        JsonNode response = protocol.handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"), null, true).orElseThrow();

        JsonNode statusEnum = StreamSupport.stream(response.path("result").path("tools").spliterator(), false)
                .filter(tool -> "update_agent_status".equals(tool.path("name").asString()))
                .findFirst().orElseThrow()
                .path("inputSchema").path("properties").path("status").path("enum");
        assertThat(statusEnum).extracting(JsonNode::asString).containsExactly(
                "PLAN_PENDING", "IN_PROGRESS", "REVIEW_PENDING", "SHIPPING", "CI_POLLING", "CI_FAILED");
    }

    @Test
    void rejectsToolCallWhenRequiredArgumentIsMissing(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, state, groups());

        JsonNode response = protocol.handle(mapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"write_task_context\",\"arguments\":{\"taskId\":\"X\"}}}"),
                null, true).orElseThrow();

        JsonNode result = response.path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asString())
                .contains("instructions").contains("required");
    }

    @Test
    void answersParseErrorWithJsonRpcCode32700(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, state, groups());

        JsonNode error = protocol.parseError("unexpected character");

        assertThat(error.path("error").path("code").asInt()).isEqualTo(-32700);
    }

    @Test
    void namesTheFailureKindWhenTheCauseCarriesNoMessage() {
        JsonMapper mapper = new JsonMapper();
        StateService state = mock(StateService.class);
        when(state.findByWorktree(any())).thenThrow(new IllegalStateException());
        McpProtocolService protocol = new McpProtocolService(mapper, state, groups());

        JsonNode error = protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"}"),
                "/nowhere", true).orElseThrow().path("error");

        assertThat(error.path("code").asInt()).isEqualTo(-32603);
        assertThat(error.path("message").asString()).isEqualTo("IllegalStateException");
    }

    @Test
    void namesTheFailureKindWhenAToolThrowsWithoutAMessage(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpTools failing = tools -> tools.tool("boom", Audience.ANYONE, NoArguments.schema("throws"),
                NoArguments.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> { throw new IllegalStateException(); });
        McpProtocolService protocol = new McpProtocolService(mapper, state, List.of(failing));

        JsonNode response = protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}"), null, true).orElseThrow();

        assertThat(response.path("result").path("content").get(0).path("text").asString())
                .endsWith(": IllegalStateException");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsARefusedMessageAsInfoBecauseTheCallerAlreadyHasTheAnswer(@TempDir Path root) {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger protocolLog = (Logger) LoggerFactory.getLogger(McpProtocolService.class);
        protocolLog.addAppender(log);
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpTools refusing = tools -> tools.tool("boom", Audience.ANYONE, NoArguments.schema("throws"),
                NoArguments.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> { throw new IllegalArgumentException("CI_POLLING cannot be reported"); });
        McpProtocolService protocol = new McpProtocolService(mapper, state, List.of(refusing));

        protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}"), null, true);

        assertThat(List.copyOf(log.list))
                .filteredOn(event -> "mcp tool failed".equals(event.getMessage()))
                .extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.INFO);
        protocolLog.detachAppender(log);
    }

    @Test
    void tellsTheAgentAFailedCallIsNotWorthRepeating(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpTools failing = tools -> tools.tool("boom", Audience.ANYONE, NoArguments.schema("throws"),
                NoArguments.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> { throw new IllegalStateException("branch ABC-1 is checked out elsewhere"); });
        McpProtocolService protocol = new McpProtocolService(mapper, state, List.of(failing));

        JsonNode result = protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}"), null, true).orElseThrow().path("result");

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("structuredContent").path("category").asString()).isEqualTo("business");
        assertThat(result.path("structuredContent").path("retryable").asBoolean()).isFalse();
        assertThat(result.path("content").get(0).path("text").asString())
                .isEqualTo("Error (business, not retryable): branch ABC-1 is checked out elsewhere");
    }

    @Test
    void listsASubAgentNoToolThatWritesOutsideItsWorktree(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
                .alias("a1").build());
        McpProtocolService protocol = new McpProtocolService(mapper, state,
                List.of(new DeployTools(mock(CommandService.class))));

        JsonNode listed = protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/list\"}"),
                root.toString(), false).orElseThrow().path("result").path("tools");

        assertThat(listed).isEmpty();
    }

    @Test
    void listsTheMasterTheToolsThatWriteASharedBranch(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, state,
                List.of(new DeployTools(mock(CommandService.class))));

        JsonNode listed = protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/list\"}"),
                null, true).orElseThrow().path("result").path("tools");

        assertThat(listed).extracting(tool -> tool.path("name").asString())
                .containsExactly("deploy_task", "revert_task");
    }

    @Test
    void countsAnyRequestFromWorktreeAsKeepAlive(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(1000).alias("a1").build());
        McpProtocolService protocol = new McpProtocolService(mapper, state, groups());

        protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}"), root.toString(), false);

        assertThat(state.task("ABC-1").orElseThrow().lastActiveTimestamp()).isGreaterThan(1000);
    }

    @Test
    void skipsKeepAliveRewriteWhenAgentWasActiveSecondsAgo(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        long freshTimestamp = System.currentTimeMillis();
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(freshTimestamp).alias("a1").build());
        McpProtocolService protocol = new McpProtocolService(mapper, state, groups());

        protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}"), root.toString(), false);

        assertThat(state.task("ABC-1").orElseThrow().lastActiveTimestamp()).isEqualTo(freshTimestamp);
    }

    @Test
    void answersAnUnknownToolWithTheToolsThisCallerHas(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(System.currentTimeMillis()).alias("a1").build());
        CallerScope scope = new CallerScope(state);
        McpProtocolService protocol = new McpProtocolService(mapper, state, List.of(
                new DeployTools(mock(CommandService.class)), new StatusTools(mock(AgentStatusReports.class), scope)));

        String text = protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"report_status\",\"arguments\":{}}}"), root.toString(), false)
                .orElseThrow().path("result").path("content").get(0).path("text").asString();

        assertThat(text).endsWith("Unknown tool: report_status. Yours: update_agent_status, notify_user");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "/no/task/here")
    void refusesACallerThatIsNeitherATaskNorTheMaster(String cwd, @TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, state,
                List.of(new DeployTools(mock(CommandService.class))));

        JsonNode error = protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"deploy_task\",\"arguments\":{\"taskId\":\"ABC-1\"}}}"), cwd, false)
                .orElseThrow().path("error");

        assertThat(error.path("code").asInt()).isEqualTo(McpProtocolService.UNKNOWN_CALLER);
    }
}
