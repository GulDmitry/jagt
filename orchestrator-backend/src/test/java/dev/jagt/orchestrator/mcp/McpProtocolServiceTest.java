package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpProtocolServiceTest {

    @Test
    void advertisesStatusEnumMatchingTaskStatusValues(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, mock(OrchestratorTools.class), state);

        JsonNode response = protocol.handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"), null).orElseThrow();

        JsonNode statusEnum = StreamSupport.stream(response.path("result").path("tools").spliterator(), false)
                .filter(tool -> "update_agent_status".equals(tool.path("name").asText()))
                .findFirst().orElseThrow()
                .path("inputSchema").path("properties").path("status").path("enum");
        assertThat(statusEnum).extracting(JsonNode::asText).containsExactly(
                "NEW", "IN_PROGRESS", "REVIEW_PENDING", "SHIPPING", "CI_POLLING", "CI_FAILED", "REVIEWED",
                "APPROVED", "DEPLOY_CONFLICT", "DEPLOYED", "DONE");
    }

    @Test
    void rejectsToolCallWhenRequiredArgumentIsMissing(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, mock(OrchestratorTools.class), state);

        JsonNode response = protocol.handle(mapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"write_task_context\",\"arguments\":{\"taskId\":\"X\"}}}"),
                null).orElseThrow();

        JsonNode result = response.path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asText())
                .contains("instructions").contains("required");
    }

    @Test
    void answersParseErrorWithJsonRpcCode32700(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        McpProtocolService protocol = new McpProtocolService(mapper, mock(OrchestratorTools.class), state);

        JsonNode error = protocol.parseError("unexpected character");

        assertThat(error.path("error").path("code").asInt()).isEqualTo(-32700);
    }

    @Test
    void countsAnyRequestFromWorktreeAsKeepAlive(@TempDir Path root) {
        JsonMapper mapper = new JsonMapper();
        StateService state = new StateService(mapper, new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).lastActiveTimestamp(1000).alias("a1").build());
        McpProtocolService protocol = new McpProtocolService(mapper, mock(OrchestratorTools.class), state);

        protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}"), root.toString());

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
        McpProtocolService protocol = new McpProtocolService(mapper, mock(OrchestratorTools.class), state);

        protocol.handle(mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}"), root.toString());

        assertThat(state.task("ABC-1").orElseThrow().lastActiveTimestamp()).isEqualTo(freshTimestamp);
    }
}
