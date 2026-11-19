package dev.jagt.orchestrator.surface.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.protocol.message.Message;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.log.DebugReads;
import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.Effect;
import dev.jagt.orchestrator.surface.mcp.McpProtocolService;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.MessageHandler;
import dev.jagt.orchestrator.surface.mcp.TestStateServiceBuilder;
import dev.jagt.orchestrator.surface.ui.log.LogReads;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DebugToolsTest {

  private static final class Registered implements McpToolRegistry {

    private final Map<String, String> tools = new HashMap<>();

    @Override
    public <T extends Message> void tool(
        String name,
        Audience audience,
        Effect effect,
        Schema schema,
        Class<T> message,
        BiFunction<T, @Nullable String, MessageContext> context,
        MessageHandler<T> handler) {
      tools.put(name, audience + " " + effect);
    }
  }

  @ParameterizedTest
  @CsvSource({"read_record", "read_transcript"})
  void declaresEachDebugReadAsTheRootsAndAsChangingNothing(String tool) {
    Registered registered = new Registered();

    new DebugTools(mock(LogReads.class), mock(DebugReads.class)).declare(registered);

    assertThat(registered.tools).containsEntry(tool, "ROOT REPORTS");
  }

  @ParameterizedTest
  @CsvSource({"read_record, '{\"record\":\"stops\"}'", "read_transcript, '{\"taskId\":\"ABC-1\"}'"})
  void refusesCallOfEachDebugReadBySubAgentAsPermission(
      String tool, String arguments, @TempDir Path root) {
    JsonMapper mapper = new JsonMapper();
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(System.currentTimeMillis())
            .alias("a1")
            .build());
    McpProtocolService protocol =
        new McpProtocolService(
            mapper,
            state,
            Clock.systemUTC(),
            List.of(new DebugTools(mock(LogReads.class), mock(DebugReads.class))));

    JsonNode result =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                        + "{\"name\":\""
                        + tool
                        + "\",\"arguments\":"
                        + arguments
                        + "}}"),
                root.toString(),
                false)
            .orElseThrow()
            .path("result");

    assertThat(result.path("structuredContent").path("category").asString())
        .isEqualTo("permission");
  }
}
