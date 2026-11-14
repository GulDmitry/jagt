package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestOpenCodeGateBuilder.openCodeGate;
import static dev.jagt.orchestrator.adapter.assistant.TestOpenCodeReadBuilder.openCodeRead;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.gate.ReadScopes;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.usage.AssistantCallKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class HeadlessOpenCodeTest {

  private static final String SCHEMA =
      "{\"type\":\"object\",\"required\":[\"commits\"],"
          + "\"properties\":{\"commits\":{\"type\":\"integer\"}}}";

  private static final OpenCodeProperties OPENCODE =
      new OpenCodeProperties("opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of());

  @TempDir static Path state;

  private final Processes processes = mock(Processes.class);
  private final UsageTracker usage = mock(UsageTracker.class);
  private final ReadScopes scopes = new ReadScopes();
  private final OpenCodeGate gate =
      openCodeGate().withState(state).build(mock(McpHealth.class), scopes);
  private final HeadlessOpenCode headless = new HeadlessOpenCode(processes, OPENCODE, usage, gate);

  @Test
  void runsStandaloneOnItsOwnConfigWithTheProjectsConfigDisabledAndTheSchemaInThePrompt(
      @TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(new Processes.Result(0, "", ""));

    headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    ArgumentCaptor<Map<String, String>> env = ArgumentCaptor.captor();
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes)
        .run(eq(worktree), eq(Duration.ofMinutes(1)), env.capture(), command.capture());
    assertThat(env.getValue()).containsEntry("OPENCODE_DISABLE_PROJECT_CONFIG", "1");
    assertThat(env.getValue().get("OPENCODE_CONFIG_CONTENT"))
        .contains(
            "\"default_agent\":\"jagt-read\"",
            "local/qwen-4b",
            "\"plugin\":[\"" + state.resolve("opencode-gate") + "\"]");
    assertThat(env.getValue().get("JAGT_READ_GATE"))
        .startsWith("http://127.0.0.1:8290/api/agent/read/");
    assertThat(command.getValue().subList(0, 5))
        .containsExactly("opencode", "run", "--standalone", "--format", "json");
    assertThat(command.getValue().getLast()).startsWith("count the commits").contains(SCHEMA);
  }

  @Test
  void mapsTheFencedObjectOfTheLastTextToTheStructuredOutputAndBooksEveryStep(
      @TempDir Path worktree) throws IOException {
    HeadlessOpenCode hearing =
        new HeadlessOpenCode(
            new FakeHearingProcesses(
                scopes,
                "Bash",
                "git log",
                new Processes.Result(
                    0, Files.readString(Path.of("src/test/resources/opencode/run.jsonl")), "")),
            OPENCODE,
            usage,
            gate);

    HeadlessReply reply = hearing.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(
            Objects.requireNonNull(reply.envelope())
                .path("structured_output")
                .path("commits")
                .asInt())
        .isEqualTo(1);
    assertThat(reply.usage().calls()).isEqualTo(4);
    verify(usage).record(eq(AssistantCallKind.ACCEPTANCE_CHECK), eq(reply.usage()));
  }

  @Test
  void answersNothingReadableWhereTheModelSaidNoObject(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(
            new Processes.Result(
                0, "{\"type\":\"text\",\"part\":{\"text\":\"I could not tell.\"}}", ""));

    HeadlessReply reply = headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(reply.envelope()).isNotNull();
    assertThat(Objects.requireNonNull(reply.envelope()).has("structured_output")).isFalse();
  }

  @Test
  void failsWithTheExitAndStillBooksWhatTheRunSpentWhereTheCliExitsNonZero(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(
            new Processes.Result(
                1,
                "{\"type\":\"step_finish\",\"part\":{\"cost\":0,\"tokens\":{\"input\":9,\"output"
                    + "\":1,\"cache\":{\"read\":0,\"write\":0}}}}",
                "model not found"));

    HeadlessReply reply = headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(reply.envelope()).isNull();
    assertThat(reply.cause()).isEqualTo("exit 1");
    verify(usage).record(eq(AssistantCallKind.ACCEPTANCE_CHECK), eq(reply.usage()));
  }

  @Test
  void holdsFenceOfTheReadsScopeWhileTheRunLastsAndForgetsItWhenTheRunEnds(@TempDir Path worktree) {
    AtomicReference<String> fence = new AtomicReference<>();
    AtomicBoolean held = new AtomicBoolean();
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenAnswer(call -> {
          String url = call.<Map<String, String>>getArgument(2).get("JAGT_READ_GATE");
          fence.set(
              Objects.requireNonNull(url)
                  .substring(Objects.requireNonNull(url).lastIndexOf('/') + 1));
          held.set(scopes.ask(fence.get()).isPresent());
          return new Processes.Result(0, "", "");
        });

    headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(held).isTrue();
    assertThat(scopes.ask(fence.get())).isEmpty();
  }

  @Test
  void discardsTheAnswerWhereCallCompletedAndNoneReachedTheReadGateSoThePluginNeverLoaded(
      @TempDir Path worktree) throws IOException {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(
            new Processes.Result(
                0, Files.readString(Path.of("src/test/resources/opencode/run.jsonl")), ""));

    HeadlessReply reply = headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(reply.envelope()).isNull();
    assertThat(reply.cause()).isEqualTo("unfenced: no call reached the read gate");
  }

  @Test
  void keepsTheAnswerWhereNoToolWasCalledSoNothingHadToReachTheReadGate(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"type\":\"text\",\"part\":{\"text\":\"```json\\n{\\\"commits\\\":2}\\n```\"}}",
                ""));

    HeadlessReply reply = headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(
            Objects.requireNonNull(reply.envelope())
                .path("structured_output")
                .path("commits")
                .asInt())
        .isEqualTo(2);
  }

  @Test
  void keepsTheAnswerWhereTheOnlyCallWasDeniedAndSoNeverRanNorReachedTheReadGate(
      @TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"type\":\"tool_use\",\"part\":{\"tool\":\"shell\",\"state\":{\"status\":\"erro"
                    + "r\",\"input\":{\"command\":\"touch"
                    + " c.txt\"}}}}\n"
                    + "{\"type\":\"text\",\"part\":{\"text\":\"```json\\n"
                    + "{\\\"commits\\\":3}\\n"
                    + "```\"}}",
                ""));

    HeadlessReply reply = headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(
            Objects.requireNonNull(reply.envelope())
                .path("structured_output")
                .path("commits")
                .asInt())
        .isEqualTo(3);
  }

  @Test
  void discardsTheAnswerWhereCompletedReadOpenedFileTheRoleRefuses(@TempDir Path worktree) {
    String ran =
        "{\"type\":\"tool_use\",\"part\":{\"tool\":\"read\",\"state\":{\"status\":\"completed\","
            + "\"input\":{\"filePath\":\""
            + worktree.resolve("review_replies.md")
            + "\"}}}}\n";
    HeadlessOpenCode hearing =
        new HeadlessOpenCode(
            new FakeHearingProcesses(
                scopes, "Read", "review_replies.md", new Processes.Result(0, ran, "")),
            OPENCODE,
            usage,
            gate);

    HeadlessReply reply =
        hearing.run(
            openCodeRead()
                .withCwd(worktree)
                .withSchema(SCHEMA)
                .withShell(List.of())
                .withRefuseRead(List.of("**/review_replies.md"))
                .withGit(false)
                .build());

    assertThat(reply.envelope()).isNull();
    assertThat(reply.cause()).startsWith("out of scope: read ");
  }

  @Test
  void failsWithNoUsageWhereTheRunDoesNotReturnInTime(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenThrow(new IllegalStateException("Command timed out"));

    HeadlessReply reply = headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build());

    assertThat(reply.envelope()).isNull();
    assertThat(reply.cause()).startsWith("did not return");
    assertThat(reply.usage().isNone()).isTrue();
  }

  @Test
  void letsStopOfJagtThroughUnjudged(@TempDir Path worktree) {
    when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
        .thenThrow(new Processes.Stopped(List.of("opencode")));

    assertThatThrownBy(
            () -> headless.run(openCodeRead().withCwd(worktree).withSchema(SCHEMA).build()))
        .isInstanceOf(Processes.Stopped.class);
  }
}
