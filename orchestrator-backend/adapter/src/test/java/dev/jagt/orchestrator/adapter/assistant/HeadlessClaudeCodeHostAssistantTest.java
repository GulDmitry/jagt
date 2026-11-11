package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.UsageTracker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HeadlessClaudeCodeHostAssistantTest {

  private final FakeProcesses runner =
      new FakeProcesses()
          .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
  private final HeadlessClaudeCodeHostAssistant assistant =
      new HeadlessClaudeCodeHostAssistant(
          headlessClaude().build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class)));

  @Test
  void constrainsThePipelineToFiveWordsSoMergeableRequestCannotComeBackFailed() {

    assistant.readReview("https://host/mr/9");

    List<String> command = runner.command();
    assertThat(command)
        .anyMatch(
            argument ->
                argument.contains(
                    "\"pipelineStatus\":{\"type\":\"string\",\"enum\":"
                        + "[\"success\",\"failed\",\"running\",\"none\",\"unknown\"]}"));
  }

  @Test
  void asksForTheHostsPipelineListSoFailedRunCannotComeBackAsNoPipeline() {

    assistant.readReview("https://host/mr/9");

    List<String> command = runner.command();
    assertThat(command)
        .anyMatch(
            argument ->
                argument.contains("LIST this request's pipelines")
                    && argument.contains("none ONLY when that listing came back EMPTY"));
  }

  @Test
  void readsTheJobThatFailedInsideTriggeredPipelineRatherThanTheTriggerItself() {

    assistant.readReview("https://host/mr/9");

    List<String> command = runner.command();
    assertThat(command)
        .anyMatch(
            argument ->
                argument.contains("A bridge or trigger job is never that job")
                    && argument.contains("read its verdict with that tool's own MCP"));
  }

  @Test
  void keepsRedRunReadableWhenTheFailingJobsLogIsNot() {

    assistant.readReview("https://host/mr/9");

    List<String> command = runner.command();
    assertThat(command)
        .anyMatch(
            argument ->
                argument.contains("A log or verdict you could not read" + " fails nothing")
                    && argument.contains("behind a discovery or category tool")
                    && argument.contains("never a word of your own about what you could not"));
  }

  @Test
  void asksForEveryNoteOfThreadSoReplyToReplyIsNotReadAsAnAnsweredComment() {

    assistant.readReview("https://host/mr/9");

    List<String> command = runner.command();
    assertThat(command)
        .anyMatch(
            argument ->
                argument.contains(
                        "ONE entry per DISCUSSION THREAD still awaiting an answer, never one per"
                            + " note")
                    && argument.contains("keep EVERY note in it, oldest first")
                    && argument.contains(
                        "never drop a note because an earlier one already answers it"));
  }

  @Test
  void readsThreadsNotesOutOfTheEnvelopeAsOneRelayableBlock() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"exists\":true,\"approved\":false,\"pipelineStatus\":\"success\",\"openedAt\":\"\",\"threads\":[\"https://host/mr/9#note_1\\n"
                + "bot: quote the pattern\\n"
                + "dev: the rule IS a pattern\\n"
                + "bot: then bound the input\"]}}",
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().discussion().threads())
        .containsExactly(
            "https://host/mr/9#note_1\nbot: quote the pattern\n"
                + "dev: the rule IS a pattern\nbot: then bound the input");
  }

  @Test
  void readsTheHostsWordThatTheRequestWasClosedUnmerged() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"exists\":true,\"approved\":false,\"pipelineStatus\":\""
                + "none\","
                + "\"closed\":true,\"merged\":false,\"openedAt\":\"\",\"threads\":[]}}",
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().request().closed()).isTrue();
  }

  @Test
  void keepsAnOverlongThreadsNewestNotesRatherThanItsOpeningComment() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"exists\":true,\"approved\":false,\"pipelineStatus\":"
                + "\"success\",\"openedAt\":\"\",\"threads\":[\"bot:"
                + " "
                + "x".repeat(3000)
                + "\\ndev: it is bound\\nbot: then bound the input\"]}}",
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().discussion().threads())
        .singleElement()
        .asString()
        .startsWith("…")
        .endsWith("\ndev: it is bound\nbot: then bound the input");
  }

  @Test
  void datesTheRequestFromTheHostsOwnTimestampRatherThanLeavingItBrandNew() {
    runner.answering(
        new Processes.Result(
            0,
            """
            {"type":"result","is_error":false,
             "structured_output":{"exists":true,"approved":false,"pipelineStatus":"success",\
            "openedAt":"2026-08-01T09:15:00Z","threads":[]}}\
            """,
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().request().openedAt()).isEqualTo(1785575700000L);
  }

  @Test
  void leavesThreadTheAgentAnsweredLastToTheReviewer() {
    runner.answering(
        new Processes.Result(
            0,
            """
            {"type":"result","is_error":false,
             "structured_output":{"exists":true,"approved":false,"pipelineStatus":"success","openedAt":"",\
            "signedInAs":"lee","threads":["https://host/mr/9#note_1\\nmia: Why is this loop not bounded?\\n\
            lee: The page size bounds it, see Paging.java"]}}\
            """,
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().discussion().threads()).isEmpty();
    assertThat(facts.get().discussion().awaitingReviewer())
        .containsExactly(
            """
            https://host/mr/9#note_1
            mia: Why is this loop not bounded?
            lee: The page size bounds it, see Paging.java\
            """);
  }

  @Test
  void leavesTheNoteIdsModelWritesUnreadBecauseTheHostGivesThem() {
    runner.answering(
        new Processes.Result(
            0,
            """
            {"type":"result","is_error":false,
             "structured_output":{"exists":true,"approved":false,"pipelineStatus":"success","openedAt":"",\
            "signedInAs":"lee","newestNotes":["note_2","note_5"],\
            "threads":["https://host/mr/9#note_1\\nmia: Why?","https://host/mr/9#note_4\\nmia: And?"]}}\
            """,
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().discussion().newestNotes()).isEmpty();
  }

  @Test
  void cutsFailureLogTooLongToRelayIntoWorktreeFile() {
    runner.answering(
        new Processes.Result(
            0,
            """
            {"type":"result","is_error":false,
             "structured_output":{"exists":true,"approved":false,"pipelineStatus":"failed",\
            "pipelineFailure":"LOG","openedAt":"","threads":[]}}\
            """
                .replace("LOG", "x".repeat(5000)),
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().pipeline().failure()).hasSize(2001).endsWith("…");
  }

  @Test
  void relaysFailureLogOfExactlyTheLimitWhole() {
    runner.answering(
        new Processes.Result(
            0,
            """
            {"type":"result","is_error":false,
             "structured_output":{"exists":true,"approved":false,"pipelineStatus":"failed",\
            "pipelineFailure":"LOG","openedAt":"","threads":[]}}\
            """
                .replace("LOG", "x".repeat(2000)),
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().pipeline().failure()).isEqualTo("x".repeat(2000));
  }

  @Test
  void relaysThreadOfExactlyTheLimitWhole() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"exists\":true,\"approved\":false,\"pipelineStatus\":\"success\","
                + "\"openedAt\":\"\",\"threads\":[\""
                + "x".repeat(2000)
                + "\"]}}",
            ""));

    var facts = assistant.readReview("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().discussion().threads()).containsExactly("x".repeat(2000));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"ABC-42", "ftp://host/mr/9"})
  void readsNoMergeRequestFromAnythingButWebAddress(String mrUrl) {

    assertThat(assistant.readMergeRequest(mrUrl).facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"ABC-42", "ftp://host/mr/9"})
  void sweepsNoReviewFromAnythingButWebAddress(String mrUrl) {

    assertThat(assistant.readReview(mrUrl).facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void readsTheBranchesAndTheTitleOfMergeRequest() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"exists\":true,\"sourceBranch\":\"ABC-42-fix\","
                + "\"targetBranch\":\"main\",\"title\":\"Fix the widget\"}}",
            ""));

    var facts = assistant.readMergeRequest("https://host/mr/9").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().exists()).isTrue();
    assertThat(facts.get().sourceBranch()).isEqualTo("ABC-42-fix");
    assertThat(facts.get().targetBranch()).isEqualTo("main");
    assertThat(facts.get().title()).isEqualTo("Fix the widget");
  }
}
