package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.RoundReviewer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeadlessRoundReviewerTest {

    private final ProcessRunner runner = mock(ProcessRunner.class);
    private final HeadlessRoundReviewer reviewer = new HeadlessRoundReviewer(runner, ClaudeProperties.defaults(),
            AssistantProperties.empty());

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
        HeadlessRoundReviewer pinned = new HeadlessRoundReviewer(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty().withMcpConfig("/cfg/mcp.json"));
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
