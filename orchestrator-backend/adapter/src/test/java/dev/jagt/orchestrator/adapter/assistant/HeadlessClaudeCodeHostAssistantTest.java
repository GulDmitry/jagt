package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;

import dev.jagt.orchestrator.port.Processes;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.service.UsageTracker;
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

class HeadlessClaudeCodeHostAssistantTest {

    @Test
    void constrainsThePipelineToFiveWordsSoAMergeableRequestCannotComeBackFailed() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        assistant.readReview("https://host/mr/9");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anyMatch(argument -> argument.contains(
                "\"pipelineStatus\":{\"type\":\"string\",\"enum\":"
                        + "[\"success\",\"failed\",\"running\",\"none\",\"unknown\"]}"));
    }

    @Test
    void asksForTheHostsPipelineListSoAFailedRunCannotComeBackAsNoPipeline() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        assistant.readReview("https://host/mr/9");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anyMatch(argument -> argument.contains("LIST this request's pipelines")
                && argument.contains("none ONLY when that listing came back EMPTY"));
    }

    @Test
    void readsTheJobThatFailedInsideATriggeredPipelineRatherThanTheTriggerItself() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        assistant.readReview("https://host/mr/9");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anyMatch(argument -> argument.contains("A bridge or trigger job is never that job")
                && argument.contains("read its verdict with that tool's own MCP"));
    }

    @Test
    void keepsARedRunReadableWhenTheFailingJobsLogIsNot() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        assistant.readReview("https://host/mr/9");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anyMatch(argument -> argument.contains("A log or verdict you could not read"
                + " fails nothing") && argument.contains("behind a discovery or category tool")
                && argument.contains("never a word of your own about what you could not"));
    }

    @Test
    void asksForEveryNoteOfAThreadSoAReplyToAReplyIsNotReadAsAnAnsweredComment() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        assistant.readReview("https://host/mr/9");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anyMatch(argument ->
                argument.contains("ONE entry per DISCUSSION THREAD still awaiting an answer, never one per note")
                        && argument.contains("keep EVERY note in it, oldest first")
                        && argument.contains("never drop a note because an earlier one already answers it"));
    }

    @Test
    void readsAThreadsNotesOutOfTheEnvelopeAsOneRelayableBlock() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"exists\":true,\"approved\":false,\"pipelineStatus\":\"success\","
                        + "\"openedAt\":\"\",\"threads\":[\"https://host/mr/9#note_1\\nbot: quote the pattern"
                        + "\\ndev: the rule IS a pattern\\nbot: then bound the input\"]}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().threads()).containsExactly("https://host/mr/9#note_1\nbot: quote the pattern\n"
                + "dev: the rule IS a pattern\nbot: then bound the input");
    }

    @Test
    void keepsAnOverlongThreadsNewestNotesRatherThanItsOpeningComment() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"exists\":true,\"approved\":false,\"pipelineStatus\":\"success\","
                        + "\"openedAt\":\"\",\"threads\":[\"bot: " + "x".repeat(3000)
                        + "\\ndev: it is bound\\nbot: then bound the input\"]}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().threads()).singleElement().asString()
                .startsWith("…").endsWith("\ndev: it is bound\nbot: then bound the input");
    }

    @Test
    void datesTheRequestFromTheHostsOwnTimestampRatherThanLeavingItBrandNew() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "structured_output":{"exists":true,"approved":false,"pipelineStatus":"success",\
                "openedAt":"2026-08-01T09:15:00Z","threads":[]}}""", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().openedAt()).isEqualTo(1785575700000L);
    }

    @Test
    void cutsAFailureLogTooLongToRelayIntoAWorktreeFile() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "structured_output":{"exists":true,"approved":false,"pipelineStatus":"failed",\
                "pipelineFailure":"LOG","openedAt":"","threads":[]}}""".replace("LOG", "x".repeat(5000)), ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().pipelineFailure()).hasSize(2001).endsWith("…");
    }
}
