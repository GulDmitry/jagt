package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.QaDecline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeadlessClaudeQaReaderTest {

    private final ProcessRunner runner = mock(ProcessRunner.class);
    private final ReadOnlyTools reads = mock(ReadOnlyTools.class);
    private final UsageTracker usage = mock(UsageTracker.class);
    private final HeadlessClaudeQaReader reader = new HeadlessClaudeQaReader(new HeadlessClaude(
            new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), Clock.systemUTC())),
            ClaudeProperties.defaults(), AssistantProperties.empty(), usage, reads), ClaudeProperties.defaults(),
            AssistantProperties.empty());

    @TempDir
    Path worktree;

    @Test
    void holdsNoWriteToolAndCurlOnlyAsAQuietGetSoNothingItReadsCanChangeTheEnvironmentOrTheTree() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

        reader.read(new QaReader.Run("", "check ABC-42", worktree, "abc123", "", List.of()));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        List<String> argv = command.getValue();
        assertThat(argv.get(argv.indexOf("--tools") + 1)).isEqualTo("Read,Grep,Glob,Bash,WebFetch");
        assertThat(argv.subList(argv.indexOf("--allowedTools"), argv.size()))
                .contains("WebFetch", "Bash(curl -sS:*)", "Bash(git log:*)")
                .noneMatch(tool -> tool.startsWith("Write") || tool.startsWith("Bash(curl") && !tool.equals("Bash(curl -sS:*)"));
        assertThat(argv.subList(argv.indexOf("--disallowedTools"), argv.indexOf("--allowedTools")))
                .contains("Write", "Edit", "Bash(git * --output*)");
    }

    @Test
    void letsAWebFetchReachOnlyTheEnvironmentsTheRunNames() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

        reader.read(new QaReader.Run("", "check ABC-42", worktree, "abc123", "",
                List.of("https://dev.example.test", "https://dev.example.test/version")));

        verify(reads).fence(any(), eq(new ReadScope(List.of(worktree), List.of("StructuredOutput"), true, List.of(),
                List.of("https://dev.example.test", "https://dev.example.test/version"))));
    }

    @Test
    void writesQaReportFromTheRowsTheModelAnswered() throws IOException {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"failure\":\"\",\"rows\":[{\"criterion\":\"page opens\","
                        + "\"status\":\"met\",\"evidence\":\"GET answered 200\"}]}}", ""));

        reader.read(new QaReader.Run("", "check ABC-42", worktree, "abc123", "", List.of()));

        assertThat(Files.readString(worktree.resolve("qa-report.md")))
                .contains("abc123", "page opens", "GET answered 200");
    }

    @Test
    void readsTheDisputeQaFilledAgainstASessionsDecline() {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"failure\":\"\",\"dispute\":\"GET /list is unsorted\"}}", ""));

        QaDecline decline = reader.reconsider(new QaReader.Run("", "decide", worktree, "abc123", "", List.of())).facts()
                .orElseThrow();

        assertThat(decline).isEqualTo(new QaDecline("abc123", "", "GET /list is unsorted"));
    }
}
