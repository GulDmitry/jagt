package dev.jagt.orchestrator.surface.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.message.NoArguments;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.report.AgentStatusReports;
import dev.jagt.orchestrator.service.report.OwnStatusReports;
import dev.jagt.orchestrator.surface.mcp.tools.DeployTools;
import dev.jagt.orchestrator.surface.mcp.tools.IdeTools;
import dev.jagt.orchestrator.surface.mcp.tools.SessionTools;
import dev.jagt.orchestrator.surface.mcp.tools.StatusTools;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class McpProtocolServiceTest {

  private final JsonMapper mapper = new JsonMapper();

  @Test
  void recordsCallPresentingOnlyTheMasterTokenAsTheRootsOwn(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    AtomicReference<ActionOrigin> seen = new AtomicReference<>();
    McpTools moving =
        tools ->
            tools.tool(
                "move",
                Audience.ANYONE,
                Effect.CHANGES,
                NoArguments.schema("moves"),
                NoArguments.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> {
                  seen.set(OriginContext.current());
                  return "moved";
                });
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of(moving));

    protocol.handle(
        mapper.readTree(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"move\",\"arguments\":{}}}"),
        null,
        true);

    assertThat(seen.get()).isEqualTo(ActionOrigin.ROOT);
  }

  @Test
  void offersSessionOnlyTheStatusesItMayReport(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(
            mapper,
            state,
            Clock.systemUTC(),
            List.of(
                new StatusTools(
                    mock(AgentStatusReports.class),
                    mock(OwnStatusReports.class),
                    new CallerScope(state),
                    mock(Notifications.class))));

    JsonNode response =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"),
                null,
                true)
            .orElseThrow();

    JsonNode statusEnum =
        StreamSupport.stream(response.path("result").path("tools").spliterator(), false)
            .filter(tool -> "update_agent_status".equals(tool.path("name").asString()))
            .findFirst()
            .orElseThrow()
            .path("inputSchema")
            .path("properties")
            .path("status")
            .path("enum");
    assertThat(statusEnum)
        .extracting(JsonNode::asString)
        .containsExactly(
            "PLAN_PENDING", "IN_PROGRESS", "REVIEW_PENDING", "SHIPPING", "CI_POLLING", "CI_FAILED");
  }

  @Test
  void rejectsToolCallWhenRequiredArgumentIsMissing(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(
            mapper,
            state,
            Clock.systemUTC(),
            List.of(new SessionTools(mock(AgentSessions.class), mock(CommandService.class))));

    JsonNode response =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"write_task_context\",\"arguments\":{\"taskId\":"
                        + "\"X\"}}}"),
                null,
                true)
            .orElseThrow();

    JsonNode result = response.path("result");
    assertThat(result.path("isError").asBoolean()).isTrue();
    assertThat(result.path("content").get(0).path("text").asString())
        .contains("instructions")
        .contains("required");
  }

  @Test
  void answersParseErrorWithJsonRpcCode32700(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());

    JsonNode error = protocol.parseError("unexpected character");

    assertThat(error.path("error").path("code").asInt()).isEqualTo(-32700);
  }

  @Test
  void namesTheFailureKindWhenTheCauseCarriesNoMessage() {
    StateService state = mock(StateService.class);
    when(state.findByWorktree(any())).thenThrow(new IllegalStateException());
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());

    JsonNode error =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"}"),
                "/nowhere",
                true)
            .orElseThrow()
            .path("error");

    assertThat(error.path("code").asInt()).isEqualTo(-32603);
    assertThat(error.path("message").asString()).isEqualTo("IllegalStateException");
  }

  @Test
  void namesTheFailureKindWhenToolThrowsWithoutMessage(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpTools failing = new ThrowingMcpTools(new IllegalStateException());
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of(failing));

    JsonNode response =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}"),
                null,
                true)
            .orElseThrow();

    assertThat(response.path("result").path("content").get(0).path("text").asString())
        .endsWith(": IllegalStateException");
  }

  @Test
  void logsSessionsCallWithItsArgumentsAndTheTurnItAnswers(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
            .build()
            .withTurn(Turn.none().relayed("ship", "d1", "", 0)));
    McpTools noting = new AnsweringMcpTools("note", Effect.REPORTS, "notes", "noted");
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of(noting));

    ((Logger) LoggerFactory.getLogger(McpProtocolService.class)).setLevel(Level.DEBUG);
    try (FakeLogCapture log = new FakeLogCapture(McpProtocolService.class)) {
      protocol.handle(
          mapper.readTree(
              "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"note\",\"arguments\":{\"why\":\"pushed\"}}}"),
          "/wt/ABC-1-proj",
          false);

      assertThat(log.events())
          .filteredOn(event -> "mcp tool called".equals(event.getMessage()))
          .singleElement()
          .satisfies(event ->
              assertThat(event.getKeyValuePairs())
                  .extracting(pair -> pair.key + "=" + pair.value)
                  .containsExactly(
                      "task=ABC-1",
                      "tool=note",
                      "turn=1",
                      "step=ship",
                      "arguments={\"why\":\"pushed\"}"));
    }
  }

  @Test
  void logsRefusedMessageAsInfoBecauseTheCallerAlreadyHasTheAnswer(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpTools refusing =
        new ThrowingMcpTools(new IllegalArgumentException("CI_POLLING cannot be reported"));
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of(refusing));

    try (FakeLogCapture log = new FakeLogCapture(McpProtocolService.class)) {
      protocol.handle(
          mapper.readTree(
              "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                  + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}"),
          null,
          true);

      assertThat(log.events())
          .filteredOn(event -> "mcp tool failed".equals(event.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.INFO);
    }
  }

  @Test
  void tellsTheAgentFailedCallIsNotWorthRepeating(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpTools failing =
        new ThrowingMcpTools(new IllegalStateException("branch ABC-1 is checked out elsewhere"));
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of(failing));

    JsonNode result =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}"),
                null,
                true)
            .orElseThrow()
            .path("result");

    assertThat(result.path("isError").asBoolean()).isTrue();
    assertThat(result.path("structuredContent").path("category").asString()).isEqualTo("business");
    assertThat(result.path("structuredContent").path("retryable").asBoolean()).isFalse();
    assertThat(result.path("content").get(0).path("text").asString())
        .isEqualTo("Error (business, not retryable): branch ABC-1 is checked out elsewhere");
  }

  @Test
  void tellsTheAgentTransientFailureIsWorthRepeating(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpTools failing =
        new ThrowingMcpTools(new UncheckedIOException(new IOException("state.json locked")));
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of(failing));

    JsonNode result =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}"),
                null,
                true)
            .orElseThrow()
            .path("result");

    assertThat(result.path("structuredContent").path("retryable").asBoolean()).isTrue();
    assertThat(result.path("content").get(0).path("text").asString())
        .startsWith("Error (transient, retryable): ");
  }

  @Test
  void answersWhatToolReturnedAsTheTextOfItsResult(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpTools echoing = new AnsweringMcpTools("echo", Effect.CHANGES, "answers", "all done");
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of(echoing));

    JsonNode result =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"echo\",\"arguments\":{}}}"),
                null,
                true)
            .orElseThrow()
            .path("result");

    assertThat(result.path("content").get(0).path("text").asString()).isEqualTo("all done");
  }

  @Test
  void answersAnInitializeWithTheProtocolVersionTheClientAsked(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());

    JsonNode result =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                        + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}"),
                null,
                true)
            .orElseThrow()
            .path("result");

    assertThat(result.path("protocolVersion").asString()).isEqualTo("2025-03-26");
    assertThat(result.path("serverInfo").path("name").asString()).isEqualTo("jagt-orchestrator");
  }

  @Test
  void answersMethodItDoesNotKnowWithMethodNotFound(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());

    JsonNode error =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}"),
                null,
                true)
            .orElseThrow()
            .path("error");

    assertThat(error.path("code").asInt()).isEqualTo(-32601);
    assertThat(error.path("message").asString()).contains("resources/list");
  }

  @Test
  void refusesAnUnknownCallersNotificationWithAnErrorCarryingNoId(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());

    JsonNode response =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"),
                "/nowhere",
                false)
            .orElseThrow();

    assertThat(response.path("error").path("code").asInt())
        .isEqualTo(McpProtocolService.UNKNOWN_CALLER);
    assertThat(response.path("id").isNull()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listsTaskNoToolThatWritesSharedBranchWhetherOrNotItPresentsTheMastersToken(
      boolean master, @TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).alias("a1").build());
    McpProtocolService protocol =
        new McpProtocolService(
            mapper, state, Clock.systemUTC(), List.of(new DeployTools(mock(CommandService.class))));

    JsonNode listed =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/list\"}"),
                root.toString(),
                master)
            .orElseThrow()
            .path("result")
            .path("tools");

    assertThat(listed).isEmpty();
  }

  @Test
  void listsTheMasterTheToolsThatWriteSharedBranchAsChangingSomething(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(
            mapper, state, Clock.systemUTC(), List.of(new DeployTools(mock(CommandService.class))));

    JsonNode listed =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/list\"}"),
                null,
                true)
            .orElseThrow()
            .path("result")
            .path("tools");

    assertThat(listed)
        .extracting(tool -> tool.path("name").asString())
        .containsExactly("deploy_task", "revert_task");
    assertThat(listed)
        .extracting(tool -> tool.path("annotations").path("readOnlyHint").asBoolean(true))
        .containsExactly(false, false);
  }

  @Test
  void marksEveryToolTaskSeesReadOnlySoReadOnlyClientLetsItReport(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).alias("a1").build());
    CallerScope scope = new CallerScope(state);
    McpProtocolService protocol =
        new McpProtocolService(
            mapper,
            state,
            Clock.systemUTC(),
            List.of(
                new StatusTools(
                    mock(AgentStatusReports.class),
                    mock(OwnStatusReports.class),
                    scope,
                    mock(Notifications.class)),
                new IdeTools(mock(CommandService.class), scope),
                new DeployTools(mock(CommandService.class))));

    JsonNode listed =
        protocol
            .handle(
                mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/list\"}"),
                root.toString(),
                false)
            .orElseThrow()
            .path("result")
            .path("tools");

    assertThat(listed)
        .extracting(tool -> tool.path("name").asString())
        .containsExactlyInAnyOrder("update_agent_status", "notify_user", "open_in_ide");
    assertThat(listed)
        .extracting(tool -> tool.path("annotations").path("readOnlyHint").asBoolean(false))
        .containsOnly(true);
  }

  @Test
  void countsAnyRequestFromWorktreeAsKeepAlive(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(1000)
            .alias("a1")
            .build());
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());

    protocol.handle(
        mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}"),
        root.toString(),
        false);

    assertThat(state.task("ABC-1").orElseThrow().lastActiveTimestamp()).isGreaterThan(1000);
  }

  @ParameterizedTest
  @CsvSource({"1000, 100000", "95000, 0"})
  void signsTheSessionCalledOnlyWhenTheKeepAliveWrites(
      long lastActive, long signedAt, @TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(lastActive)
            .alias("a1")
            .build());
    McpProtocolService protocol =
        new McpProtocolService(
            mapper, state, Clock.fixed(Instant.ofEpochMilli(100_000), ZoneOffset.UTC), List.of());

    protocol.handle(
        mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}"),
        root.toString(),
        false);

    assertThat(state.task("ABC-1").orElseThrow().session().movedAt()).isEqualTo(signedAt);
  }

  @Test
  void skipsKeepAliveRewriteWhenAgentWasActiveSecondsAgo(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    long freshTimestamp = System.currentTimeMillis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(freshTimestamp)
            .alias("a1")
            .build());
    McpProtocolService protocol =
        new McpProtocolService(mapper, state, Clock.systemUTC(), List.of());

    protocol.handle(
        mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}"),
        root.toString(),
        false);

    assertThat(state.task("ABC-1").orElseThrow().lastActiveTimestamp()).isEqualTo(freshTimestamp);
  }

  @Test
  void skipsKeepAliveRewriteWhenAgentWasActiveExactlyTheThrottleAgo(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(85_000)
            .alias("a1")
            .build());
    McpProtocolService protocol =
        new McpProtocolService(
            mapper, state, Clock.fixed(Instant.ofEpochMilli(100_000), ZoneOffset.UTC), List.of());

    protocol.handle(
        mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}"),
        root.toString(),
        false);

    assertThat(state.task("ABC-1").orElseThrow().lastActiveTimestamp()).isEqualTo(85_000);
  }

  @Test
  void answersAnUnknownToolWithTheToolsThisCallerHas(@TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(System.currentTimeMillis())
            .alias("a1")
            .build());
    CallerScope scope = new CallerScope(state);
    McpProtocolService protocol =
        new McpProtocolService(
            mapper,
            state,
            Clock.systemUTC(),
            List.of(
                new DeployTools(mock(CommandService.class)),
                new StatusTools(
                    mock(AgentStatusReports.class),
                    mock(OwnStatusReports.class),
                    scope,
                    mock(Notifications.class))));

    String text =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"report_status\",\"arguments\":{}}}"),
                root.toString(),
                false)
            .orElseThrow()
            .path("result")
            .path("content")
            .get(0)
            .path("text")
            .asString();

    assertThat(text)
        .endsWith("Unknown tool: report_status. Yours: update_agent_status, notify_user");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "/no/task/here")
  void refusesCallerThatIsNeitherTaskNorTheMaster(String cwd, @TempDir Path root) {
    StateService state = TestStateServiceBuilder.stateService().build(mapper, root);
    McpProtocolService protocol =
        new McpProtocolService(
            mapper, state, Clock.systemUTC(), List.of(new DeployTools(mock(CommandService.class))));

    JsonNode response =
        protocol
            .handle(
                mapper.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"deploy_task\",\"arguments\":{\"taskId\":\"ABC-1"
                        + "\"}}}"),
                cwd,
                false)
            .orElseThrow();

    assertThat(response.path("error").path("code").asInt())
        .isEqualTo(McpProtocolService.UNKNOWN_CALLER);
    assertThat(response.path("id").asInt()).isEqualTo(10);
  }
}
