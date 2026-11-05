package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeadlessOpenCodeTest {

    private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"commits\"],"
            + "\"properties\":{\"commits\":{\"type\":\"integer\"}}}";

    private final Processes processes = mock(Processes.class);
    private final UsageTracker usage = mock(UsageTracker.class);
    private final HeadlessOpenCode headless = new HeadlessOpenCode(processes,
            new OpenCodeProperties("opencode", "http://127.0.0.1:8080/v1", "qwen-4b", null), usage);

    @Test
    void runsStandaloneOnItsOwnConfigWithTheProjectsConfigDisabledAndTheSchemaInThePrompt(@TempDir Path worktree) {
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
                .thenReturn(new Processes.Result(0, "", ""));

        headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(worktree), List.of()),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1)));

        ArgumentCaptor<Map<String, String>> env = ArgumentCaptor.captor();
        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).run(eq(worktree), eq(Duration.ofMinutes(1)), env.capture(), command.capture());
        assertThat(env.getValue()).containsEntry("OPENCODE_DISABLE_PROJECT_CONFIG", "1");
        assertThat(env.getValue().get("OPENCODE_CONFIG_CONTENT")).contains("\"default_agent\":\"jagt-read\"", "local/qwen-4b");
        assertThat(command.getValue().subList(0, 5)).containsExactly("opencode", "run", "--standalone", "--format", "json");
        assertThat(command.getValue().getLast()).startsWith("count the commits").contains(SCHEMA);
    }

    @Test
    void mapsTheFencedObjectOfTheLastTextToTheStructuredOutputAndBooksEveryStep(@TempDir Path worktree) throws IOException {
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
                .thenReturn(new Processes.Result(0, Files.readString(Path.of("src/test/resources/opencode/run.jsonl")), ""));

        HeadlessReply reply = headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(worktree), List.of()),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1)));

        assertThat(reply.envelope().path("structured_output").path("commits").asInt()).isEqualTo(1);
        assertThat(reply.usage().calls()).isEqualTo(4);
        verify(usage).record(eq(AssistantCallKind.QA_CHECK), eq(reply.usage()));
    }

    @Test
    void answersNothingReadableWhereTheModelSaidNoObject(@TempDir Path worktree) {
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class))).thenReturn(new Processes.Result(0,
                "{\"type\":\"text\",\"part\":{\"text\":\"I could not tell.\"}}", ""));

        HeadlessReply reply = headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(worktree), List.of()),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1)));

        assertThat(reply.envelope()).isNotNull();
        assertThat(reply.envelope().has("structured_output")).isFalse();
    }

    @Test
    void failsWithTheExitAndStillBooksWhatTheRunSpentWhereTheCliExitsNonZero(@TempDir Path worktree) {
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class))).thenReturn(new Processes.Result(1,
                "{\"type\":\"step_finish\",\"part\":{\"cost\":0,\"tokens\":{\"input\":9,\"output\":1,\"cache\":{\"read\":0,\"write\":0}}}}",
                "model not found"));

        HeadlessReply reply = headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(worktree), List.of()),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1)));

        assertThat(reply.envelope()).isNull();
        assertThat(reply.cause()).isEqualTo("exit 1");
        verify(usage).record(eq(AssistantCallKind.QA_CHECK), eq(reply.usage()));
    }

    @Test
    void discardsTheAnswerWhereACompletedCallRanOutsideTheScope(@TempDir Path worktree) throws IOException {
        String ran = "{\"type\":\"tool_use\",\"part\":{\"tool\":\"shell\",\"state\":{\"status\":\"completed\","
                + "\"input\":{\"command\":\"touch c.txt\"}}}}\n";
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class))).thenReturn(new Processes.Result(0,
                ran + Files.readString(Path.of("src/test/resources/opencode/run.jsonl")), ""));

        HeadlessReply reply = headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(worktree), List.of()),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1)));

        assertThat(reply.envelope()).isNull();
        assertThat(reply.cause()).startsWith("out of scope: shell touch c.txt");
    }

    @Test
    void discardsTheAnswerWhereACompletedCurlWroteAFileUnderAnAllowedGlob(@TempDir Path worktree) throws IOException {
        String ran = "{\"type\":\"tool_use\",\"part\":{\"tool\":\"shell\",\"state\":{\"status\":\"completed\","
                + "\"input\":{\"command\":\"curl -sS -o f https://dev.example.test/\"}}}}\n";
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class))).thenReturn(new Processes.Result(0,
                ran + Files.readString(Path.of("src/test/resources/opencode/run.jsonl")), ""));

        HeadlessReply reply = headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(curl -sS:*)"), List.of(), true, false, List.of(worktree),
                        List.of("https://dev.example.test/")),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1)));

        assertThat(reply.envelope()).isNull();
        assertThat(reply.cause()).startsWith("out of scope: shell curl -sS -o f");
    }

    @Test
    void failsWithNoUsageWhereTheRunDoesNotReturnInTime(@TempDir Path worktree) {
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
                .thenThrow(new IllegalStateException("Command timed out"));

        HeadlessReply reply = headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(worktree), List.of()),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1)));

        assertThat(reply.envelope()).isNull();
        assertThat(reply.cause()).startsWith("did not return");
        assertThat(reply.usage().isNone()).isTrue();
    }

    @Test
    void letsAStopOfJagtThroughUnjudged(@TempDir Path worktree) {
        when(processes.run(any(Path.class), any(Duration.class), any(Map.class), any(List.class)))
                .thenThrow(new Processes.Stopped(List.of("opencode")));

        assertThatThrownBy(() -> headless.run(new OpenCodeRead(worktree, "count the commits", SCHEMA, "qwen-4b",
                OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, true, List.of(worktree), List.of()),
                AssistantCallKind.QA_CHECK, Duration.ofMinutes(1))))
                .isInstanceOf(Processes.Stopped.class);
    }
}
