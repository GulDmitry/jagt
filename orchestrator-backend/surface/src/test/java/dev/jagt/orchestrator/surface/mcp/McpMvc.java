package dev.jagt.orchestrator.surface.mcp;

import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.surface.mcp.tools.DeployTools;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

final class McpMvc {

  private McpMvc() {}

  static MockMvc over(McpProtocolService protocol) {
    return over(protocol, new JsonMapper());
  }

  static MockMvc over(McpProtocolService protocol, JsonMapper mapper) {
    return MockMvcBuilders.standaloneSetup(
            new McpController(protocol, mock(StateService.class), mapper, mock(MasterToken.class)))
        .build();
  }

  static McpProtocolService plain(JsonMapper mapper, StateService state) {
    return new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());
  }

  static McpProtocolService frozenAt100s(JsonMapper mapper, StateService state) {
    return new McpProtocolService(
        mapper,
        state,
        Clock.fixed(Instant.ofEpochMilli(100_000), ZoneOffset.UTC),
        List.of());
  }

  static TaskState taskAt(Path root) {
    return TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).alias("a1").build();
  }

  static TaskState taskAt(Path root, long lastActive) {
    return TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
        .lastActiveTimestamp(lastActive)
        .alias("a1")
        .build();
  }

  static McpProtocolService deployingOver(JsonMapper mapper, Path root) {
    return deploying(mapper, stateIn(mapper, root));
  }

  static StateService stateIn(JsonMapper mapper, Path root) {
    return new StateService(
        mapper,
        pathsIn(root),
        new TaskReducer(new TaskChart(Composition.builtIn())),
        event -> {});
  }

  static McpProtocolService deploying(JsonMapper mapper, StateService state) {
    return new McpProtocolService(
        mapper, state, Clock.systemUTC(), List.of(new DeployTools(mock(CommandService.class))));
  }

  static OrchestratorPaths pathsIn(Path root) {
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString()));
  }

  static MockMvc overRealProtocol(
      JsonMapper mapper, OrchestratorPaths paths, StateService state) {
    return MockMvcBuilders.standaloneSetup(
            new McpController(
                new McpProtocolService(mapper, state, Clock.systemUTC(), List.of()),
                state,
                mapper,
                new MasterToken(paths)))
        .build();
  }
}
