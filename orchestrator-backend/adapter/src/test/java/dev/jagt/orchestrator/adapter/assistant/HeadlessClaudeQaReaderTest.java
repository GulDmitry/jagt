package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.UsageTracker;
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
    void holdsNoBashCurlAndNoWriteToolSoNothingItReadsCanChangeTheEnvironmentOrTheTree() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"failure\":\"\",\"rows\":[]}}", ""));

        reader.read(new QaReader.Run("", "check ABC-42", worktree, "abc123", ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        List<String> argv = command.getValue();
        assertThat(argv.get(argv.indexOf("--tools") + 1)).isEqualTo("Read,Grep,Glob,Bash,WebFetch");
        assertThat(argv.subList(argv.indexOf("--allowedTools"), argv.size()))
                .contains("WebFetch", "Bash(git log:*)")
                .noneMatch(tool -> tool.contains("curl") || tool.startsWith("Write"));
        assertThat(argv.subList(argv.indexOf("--disallowedTools"), argv.indexOf("--allowedTools")))
                .contains("Write", "Edit", "Bash(git * --output*)");
    }

    @Test
    void writesQaReportFromTheRowsTheModelAnswered() throws IOException {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"failure\":\"\",\"rows\":[{\"criterion\":\"page opens\","
                        + "\"status\":\"met\",\"evidence\":\"GET answered 200\"}]}}", ""));

        reader.read(new QaReader.Run("", "check ABC-42", worktree, "abc123", ""));

        assertThat(Files.readString(worktree.resolve("qa-report.md")))
                .contains("abc123", "page opens", "GET answered 200");
    }
}
