package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;

import dev.jagt.orchestrator.port.Processes;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.service.UsageTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HeadlessClaudeCodeHostAssistantTest {

    @Test
    void constrainsThePipelineToFiveWordsSoAMergeableRequestCannotComeBackFailed() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

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
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

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
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

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
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

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
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

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
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

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
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

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
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().openedAt()).isEqualTo(1785575700000L);
    }

    @Test
    void leavesAThreadTheAgentAnsweredLastToTheReviewer() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "structured_output":{"exists":true,"approved":false,"pipelineStatus":"success","openedAt":"",\
                "signedInAs":"lee","threads":["https://host/mr/9#note_1\\nmia: Why is this loop not bounded?\\n\
                lee: The page size bounds it, see Paging.java"]}}""", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().threads()).isEmpty();
        assertThat(facts.get().awaitingReviewer()).containsExactly(
                "https://host/mr/9#note_1\nmia: Why is this loop not bounded?\nlee: The page size bounds it, see Paging.java");
    }

    @Test
    void carriesTheNewestNoteIdOfEachOpenThreadAlongWithIt() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "structured_output":{"exists":true,"approved":false,"pipelineStatus":"success","openedAt":"",\
                "signedInAs":"lee","newestNotes":["note_2","note_5"],\
                "threads":["https://host/mr/9#note_1\\nmia: Why?","https://host/mr/9#note_4\\nmia: And?"]}}""", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().newestNotes()).containsExactly("note_2", "note_5");
    }

    @Test
    void cutsAFailureLogTooLongToRelayIntoAWorktreeFile() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "structured_output":{"exists":true,"approved":false,"pipelineStatus":"failed",\
                "pipelineFailure":"LOG","openedAt":"","threads":[]}}""".replace("LOG", "x".repeat(5000)), ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().pipelineFailure()).hasSize(2001).endsWith("…");
    }

    @Test
    void relaysAFailureLogOfExactlyTheLimitWhole() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "structured_output":{"exists":true,"approved":false,"pipelineStatus":"failed",\
                "pipelineFailure":"LOG","openedAt":"","threads":[]}}""".replace("LOG", "x".repeat(2000)), ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().pipelineFailure()).isEqualTo("x".repeat(2000));
    }

    @Test
    void relaysAThreadOfExactlyTheLimitWhole() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"exists\":true,\"approved\":false,\"pipelineStatus\":\"success\","
                        + "\"openedAt\":\"\",\"threads\":[\"" + "x".repeat(2000) + "\"]}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        var facts = assistant.readReview("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().threads()).containsExactly("x".repeat(2000));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ABC-42", "ftp://host/mr/9"})
    void readsNoMergeRequestFromAnythingButAWebAddress(String mrUrl) {
        ProcessRunner runner = mock(ProcessRunner.class);
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        assertThat(assistant.readMergeRequest(mrUrl).facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ABC-42", "ftp://host/mr/9"})
    void sweepsNoReviewFromAnythingButAWebAddress(String mrUrl) {
        ProcessRunner runner = mock(ProcessRunner.class);
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        assertThat(assistant.readReview(mrUrl).facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void readsTheBranchesAndTheTitleOfAMergeRequest() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"exists\":true,\"sourceBranch\":\"ABC-42-fix\","
                        + "\"targetBranch\":\"main\",\"title\":\"Fix the widget\"}}", ""));
        var assistant = new HeadlessClaudeCodeHostAssistant(new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class)));

        var facts = assistant.readMergeRequest("https://host/mr/9").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().exists()).isTrue();
        assertThat(facts.get().sourceBranch()).isEqualTo("ABC-42-fix");
        assertThat(facts.get().targetBranch()).isEqualTo("main");
        assertThat(facts.get().title()).isEqualTo("Fix the widget");
    }
}
