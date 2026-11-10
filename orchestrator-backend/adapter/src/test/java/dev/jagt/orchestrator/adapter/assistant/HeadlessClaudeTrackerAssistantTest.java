package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.HeadlessClaudeFixtures.answering;
import static dev.jagt.orchestrator.adapter.assistant.HeadlessClaudeFixtures.answeringNoFacts;
import static dev.jagt.orchestrator.adapter.assistant.HeadlessClaudeFixtures.claude;
import static dev.jagt.orchestrator.adapter.assistant.HeadlessClaudeFixtures.commandOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HeadlessClaudeTrackerAssistantTest {

  @Test
  void takesTheSiteIdFromTheHostsOwnListingSoSearchIsNotSentToSiteNobodyGranted() {
    ProcessRunner runner =
        answering(new Processes.Result(0, "{\"structured_output\":{\"keys\":[]}}", ""));
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    assistant.findCandidates("assignee = currentUser()");

    List<String> command = commandOf(runner);
    assertThat(command)
        .anyMatch(
            argument ->
                argument.contains(
                    "gets one the host's own listing of reachable sites answered, never one"
                        + " recalled or guessed"));
  }

  @Test
  void readsTheTicketOutOfTheEnvelopesStructuredOutput() {
    ProcessRunner runner =
        answering(
            new Processes.Result(
                0,
                """
                {"type":"result","is_error":false,"total_cost_usd":0.05,
                 "usage":{"input_tokens":10,"cache_creation_input_tokens":24000,\
                "cache_read_input_tokens":0,"output_tokens":170},
                 "structured_output":{"exists":true,"key":"ABC-42","title":"Widget layout is off",\
                "trackerProject":"ABC","labels":["backend"],"url":"https://tracker/ABC-42"}}\
                """,
                ""));
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    var facts = assistant.readTicket("ABC-42").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().key()).isEqualTo("ABC-42");
    assertThat(facts.get().title()).isEqualTo("Widget layout is off");
    assertThat(facts.get().labels()).containsExactly("backend");
  }

  @ParameterizedTest
  @ValueSource(strings = {"True", "TRUE", "tRuE"})
  void readsTheItemAsExistingWhenTheModelWritesTrueAsText(String written) {
    ProcessRunner runner =
        answering(
            new Processes.Result(
                0,
                "{\"result\":\"{\\\"exists\\\":\\\""
                    + written
                    + "\\\",\\\"failure\\\":\\\"\\\","
                    + "\\\"key\\\":\\\"ABC-42\\\",\\\"title\\\":\\\"Widget\\\"}\"}",
                ""));
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    var facts = assistant.readTicket("ABC-42").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().exists()).isTrue();
  }

  @Test
  void refusesTheAnswerAsUnusableWhenTheModelWritesBooleanAsMaybe() {
    ProcessRunner runner =
        answering(
            new Processes.Result(
                0,
                "{\"structured_output\":{\"exists\":\"maybe\",\"failure\":\"\",\"key\":\"ABC-42"
                    + "\"}}",
                ""));
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    var facts = assistant.readTicket("ABC-42").facts();

    assertThat(facts).isEmpty();
  }

  @Test
  void listsTheKeysTheSearchAnsweredWithoutBlanksOrPadding() {
    ProcessRunner runner =
        answering(
            new Processes.Result(
                0, "{\"structured_output\":{\"keys\":[\" ABC-1 \",\"\",\"ABC-2\"]}}", ""));
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    var keys = assistant.findCandidates("assignee = currentUser()").facts();

    assertThat(keys).contains(List.of("ABC-1", "ABC-2"));
  }

  @Test
  void tellsTheNextAnswerWhatTheLastOneGotWrong() {
    ProcessRunner runner = answeringNoFacts();
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    assistant.readTicket("ABC-42", List.of("the url was empty", "the key was lowercase"));

    List<String> command = commandOf(runner);
    assertThat(command)
        .anyMatch(
            argument ->
                argument.endsWith(
                    """


                    Your last answer was refused:
                    - the url was empty
                    - the key was lowercase
                    Answer again, fixing every one of those.\
                    """));
  }

  @Test
  void addsNoCorrectionToFirstAnswer() {
    ProcessRunner runner = answeringNoFacts();
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    assistant.readTicket("ABC-42", List.of());

    List<String> command = commandOf(runner);
    assertThat(command).noneMatch(argument -> argument.contains("Your last answer was refused"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void readsNoTicketWithoutReference(String ref) {
    ProcessRunner runner = mock(ProcessRunner.class);
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    assertThat(assistant.readTicket(ref, List.of()).facts()).isEmpty();
    assertThat(assistant.readTicketText(ref).facts()).isEmpty();
    verifyNoInteractions(runner);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void searchesNoTrackerWithoutQuery(String query) {
    ProcessRunner runner = mock(ProcessRunner.class);
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    assertThat(assistant.findCandidates(query).facts()).isEmpty();
    verifyNoInteractions(runner);
  }

  @Test
  void copiesTheTicketTextOutStrippedAndAnswersNothingForAnEmptyOne() {
    ProcessRunner runner =
        answering(
            new Processes.Result(
                0, "{\"structured_output\":{\"text\":\"  # Summary\\nbody  \"}}", ""),
            new Processes.Result(0, "{\"structured_output\":{\"text\":\"  \"}}", ""));
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner));

    var copied = assistant.readTicketText("ABC-42").facts();
    var empty = assistant.readTicketText("ABC-42").facts();

    assertThat(copied).contains("# Summary\nbody");
    assertThat(empty).isEmpty();
  }

  @Test
  void booksTheTicketTextCopyAsTicketTextCallNotMasterReview() {
    ProcessRunner runner =
        answering(
            new Processes.Result(
                0,
                """
                {"total_cost_usd":0.06,"usage":{"input_tokens":31000,"output_tokens":40},
                 "structured_output":{"text":"# Summary"}}\
                """,
                ""));
    UsageTracker usage = mock(UsageTracker.class);
    var assistant = new HeadlessClaudeTrackerAssistant(claude(runner, usage));

    assistant.readTicketText("ABC-42");

    verify(usage).record(AssistantCallKind.TICKET_TEXT, TokenUsage.ofCall(31_000, 0, 40, 0.06));
  }
}
