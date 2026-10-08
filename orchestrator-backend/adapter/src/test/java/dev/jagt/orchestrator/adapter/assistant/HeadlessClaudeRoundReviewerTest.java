package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.RoundReviewer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeadlessClaudeRoundReviewerTest {

    private final ProcessRunner runner = mock(ProcessRunner.class);
    private final McpHealth mcp = mock(McpHealth.class);
    private final HeadlessClaudeRoundReviewer reviewer = new HeadlessClaudeRoundReviewer(runner, ClaudeProperties.defaults(),
            AssistantProperties.empty(), mcp);

    @Test
    void allowsTheHumansOwnMcpServersSoATicketJagtDidNotQuoteStaysReadable() {
        when(mcp.servers()).thenReturn(Optional.of(List.of("plugin:acme:tracker", "claude.ai Code Host")));
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("mcp__plugin_acme_tracker", "mcp__claude_ai_Code_Host");
    }

    @Test
    void refusesTheReviewEveryWriteEveryPushAndTheAuthorsOwnReplies() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("Edit", "Write", "Bash(git push:*)", "Bash(git commit:*)",
                "Read(**/review_replies.md)");
    }

    @Test
    void runsOnlyReadOnlyGitWhateverTheDiffAsksEvenWhereTheReadsBypassPermissions() {
        HeadlessClaudeRoundReviewer bypassing = new HeadlessClaudeRoundReviewer(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty().withPermissionMode("bypassPermissions"), mcp);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        bypassing.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--permission-mode", "dontAsk")
                .containsSequence("--tools", "Read,Grep,Glob,Bash")
                .contains("Bash(git diff:*)")
                .doesNotContain("Bash", "bypassPermissions");
    }

    @Test
    void refusesGitsOutputFlagBecauseItWritesAFile() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("Bash(git * --ou*)");
    }

    @Test
    void refusesEveryMcpToolNamedAsAWrite() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("mcp__*__save*", "mcp__*__merge*", "mcp__*__create*");
    }

    @Test
    void keepsTheWorktreeOutOfTheSystemPromptSoEveryRoundSharesOneCache() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("--exclude-dynamic-system-prompt-sections");
    }

    @Test
    void refusesTheReviewEveryBuildBecauseTheTestsRanBeforeItsRoundCame() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("Bash(./gradlew:*)", "Bash(mvn:*)", "Bash(npm:*)", "Bash(timeout:*)");
    }

    @Test
    void sendsWhatEveryRoundSharesAsTheSystemPromptSoTheNextReaderFindsItCached() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        reviewer.review(new RoundReviewer.Round("judge hard", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("--append-system-prompt", "judge hard");
    }

    @Test
    void loadsOnlyTheServersPinnedForAReview() {
        HeadlessClaudeRoundReviewer pinned = new HeadlessClaudeRoundReviewer(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty().withMcpConfig("/cfg/mcp.json"), mcp);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"verdict\":\"ready\"}}", ""));

        pinned.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSubsequence("--strict-mcp-config", "--mcp-config", "/cfg/mcp.json");
    }

    @Test
    void answersARunThatPrintedNothingReadableAsAFailureRatherThanAVerdict() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(1, "", "session limit reached"));

        var answer = reviewer.review(new RoundReviewer.Round("", "review ABC-42", List.of(Path.of("/w/ABC-42")), ""));

        assertThat(answer.facts()).get().satisfies(judgement -> assertThat(judgement.failure()).isNotBlank());
    }
}
