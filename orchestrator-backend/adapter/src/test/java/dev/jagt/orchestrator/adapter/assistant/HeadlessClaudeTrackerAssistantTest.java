package dev.jagt.orchestrator.adapter.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class HeadlessClaudeTrackerAssistantTest {

  @Test
  void takesTheSiteIdFromTheHostsOwnListingSoASearchIsNotSentToASiteNobodyGranted() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"keys\":[]}}", ""));
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    assistant.findCandidates("assignee = currentUser()");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .anyMatch(
            argument ->
                argument.contains(
                    "gets one the host's own listing of reachable sites answered, never one"
                        + " recalled or guessed"));
  }

  @Test
  void readsTheTicketOutOfTheEnvelopesStructuredOutput() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
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
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    var facts = assistant.readTicket("ABC-42").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().key()).isEqualTo("ABC-42");
    assertThat(facts.get().title()).isEqualTo("Widget layout is off");
    assertThat(facts.get().labels()).containsExactly("backend");
  }

  @ParameterizedTest
  @ValueSource(strings = {"True", "TRUE", "tRuE"})
  void readsTheItemAsExistingWhenTheModelWritesTrueAsAText(String written) {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"result\":\"{\\\"exists\\\":\\\""
                    + written
                    + "\\\",\\\"failure\\\":\\\"\\\","
                    + "\\\"key\\\":\\\"ABC-42\\\",\\\"title\\\":\\\"Widget\\\"}\"}",
                ""));
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    var facts = assistant.readTicket("ABC-42").facts();

    assertThat(facts).isPresent();
    assertThat(facts.get().exists()).isTrue();
  }

  @Test
  void refusesTheAnswerAsUnusableWhenTheModelWritesABooleanAsMaybe() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                "{\"structured_output\":{\"exists\":\"maybe\",\"failure\":\"\",\"key\":\"ABC-42\"}}",
                ""));
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    var facts = assistant.readTicket("ABC-42").facts();

    assertThat(facts).isEmpty();
  }

  @Test
  void listsTheKeysTheSearchAnsweredWithoutBlanksOrPadding() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0, "{\"structured_output\":{\"keys\":[\" ABC-1 \",\"\",\"ABC-2\"]}}", ""));
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    var keys = assistant.findCandidates("assignee = currentUser()").facts();

    assertThat(keys).contains(List.of("ABC-1", "ABC-2"));
  }

  @Test
  void tellsTheNextAnswerWhatTheLastOneGotWrong() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    assistant.readTicket("ABC-42", List.of("the url was empty", "the key was lowercase"));

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .anyMatch(
            argument ->
                argument.endsWith(
                    "\n\n"
                        + "Your last answer was refused:\n"
                        + "- the url was empty\n"
                        + "- the key was lowercase\n"
                        + "Answer again, fixing every one of those."));
  }

  @Test
  void addsNoCorrectionToAFirstAnswer() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    assistant.readTicket("ABC-42", List.of());

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .noneMatch(argument -> argument.contains("Your last answer was refused"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void readsNoTicketWithoutAReference(String ref) {
    ProcessRunner runner = mock(ProcessRunner.class);
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    assertThat(assistant.readTicket(ref, List.of()).facts()).isEmpty();
    assertThat(assistant.readTicketText(ref).facts()).isEmpty();
    verifyNoInteractions(runner);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void searchesNoTrackerWithoutAQuery(String query) {
    ProcessRunner runner = mock(ProcessRunner.class);
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    assertThat(assistant.findCandidates(query).facts()).isEmpty();
    verifyNoInteractions(runner);
  }

  @Test
  void copiesTheTicketTextOutStrippedAndAnswersNothingForAnEmptyOne() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0, "{\"structured_output\":{\"text\":\"  # Summary\\nbody  \"}}", ""),
            new Processes.Result(0, "{\"structured_output\":{\"text\":\"  \"}}", ""));
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                mock(UsageTracker.class),
                mock(ReadOnlyTools.class)));

    var copied = assistant.readTicketText("ABC-42").facts();
    var empty = assistant.readTicketText("ABC-42").facts();

    assertThat(copied).contains("# Summary\nbody");
    assertThat(empty).isEmpty();
  }

  @Test
  void booksTheTicketTextCopyAsATicketTextCallNotAMasterReview() {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any()))
        .thenReturn(
            new Processes.Result(
                0,
                """
                {"total_cost_usd":0.06,"usage":{"input_tokens":31000,"output_tokens":40},
                 "structured_output":{"text":"# Summary"}}\
                """,
                ""));
    UsageTracker usage = mock(UsageTracker.class);
    var assistant =
        new HeadlessClaudeTrackerAssistant(
            new HeadlessClaude(
                new AssistantProcess(
                    runner,
                    new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC(), new dev.jagt.orchestrator.service.ReachLedger())),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                usage,
                mock(ReadOnlyTools.class)));

    assistant.readTicketText("ABC-42");

    verify(usage).record(AssistantCallKind.TICKET_TEXT, TokenUsage.ofCall(31_000, 0, 40, 0.06));
  }
}
