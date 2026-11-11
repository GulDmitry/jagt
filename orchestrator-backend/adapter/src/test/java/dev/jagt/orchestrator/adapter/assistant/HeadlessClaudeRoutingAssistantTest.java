package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RoutingQuestion;
import dev.jagt.orchestrator.task.RulePair;
import dev.jagt.orchestrator.task.TicketFacts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeadlessClaudeRoutingAssistantTest {

  private final FakeProcesses runner =
      new FakeProcesses()
          .answering(
              new Processes.Result(0, "{\"structured_output\":{\"project\":\"ledger\"}}", ""));
  private final HeadlessClaudeRoutingAssistant assistant =
      new HeadlessClaudeRoutingAssistant(
          headlessClaude().build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class)));

  @Test
  void asksNothingWhereTheQuestionNamesNoRepository() {
    var answer =
        assistant.routeProject(
            RoutingQuestion.defaults().withItem(TicketFacts.defaults().withKey("ABC-42")));

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void asksNothingWhereThereIsNoQuestion() {
    var answer = assistant.routeProject(null);

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void readsTheRepositoryAndTheRuleOutOfTheStructuredAnswer() {
    runner.answering(
        new Processes.Result(
            0, "{\"structured_output\":{\"project\":\"ledger\",\"rule\":\" invoice sums \"}}", ""));

    var answer =
        assistant.routeProject(
            RoutingQuestion.defaults()
                .withItem(TicketFacts.defaults().withKey("ABC-42").withTitle("VAT is wrong"))
                .withProjects(Map.of("ledger", "invoicing")));

    assertThat(answer.facts()).contains(new RoutingAnswer("ledger", "invoice sums"));
  }

  @Test
  void listsEachRepositoryTheRulesAndThePrecedentsInThePrompt() {
    assistant.routeProject(
        RoutingQuestion.defaults()
            .withItem(TicketFacts.defaults().withKey("ABC-42").withTitle("VAT is wrong"))
            .withProjects(Map.of("ledger", "invoicing"))
            .withRules(List.of("VAT goes to ledger"))
            .withPrecedents(List.of("ABC-7 was done in ledger")));

    List<String> command = runner.command();
    assertThat(command)
        .anySatisfy(
            argument ->
                assertThat(argument)
                    .contains("- ledger: invoicing")
                    .contains(
                        """
                        Rules this install has written, which outrank everything above:
                        VAT goes to ledger
                        """)
                    .contains(
                        """
                        Items finished before, and the repository each was done in:
                        ABC-7 was done in ledger
                        """));
  }

  @Test
  void leavesTheRuleAndPrecedentSectionsOutWhereThereAreNone() {
    assistant.routeProject(
        RoutingQuestion.defaults()
            .withItem(TicketFacts.defaults().withKey("ABC-42").withTitle("VAT is wrong"))
            .withProjects(Map.of("ledger", "invoicing")));

    List<String> command = runner.command();
    assertThat(command)
        .noneMatch(
            argument ->
                argument.contains("Rules this install has written")
                    || argument.contains("Items finished before"));
  }

  @Test
  void asksNothingAboutStaleRuleWhereNoRuleIsWritten() {
    var answer = assistant.staleRule("ABC-42", "ledger", List.of());

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void asksNothingAboutStaleRuleWhereAnArgumentIsMissing() {
    var answer = assistant.staleRule(null, "ledger", List.of("VAT goes to ledger"));

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void namesTheWrittenRuleTheHandPlacementContradicted() {
    runner.answering(
        new Processes.Result(0, "{\"structured_output\":{\"rule\":\"VAT goes to ledger\"}}", ""));

    var answer = assistant.staleRule("ABC-42", "shop", List.of("VAT goes to ledger"));

    assertThat(answer.facts()).contains("VAT goes to ledger");
    List<String> command = runner.command();
    assertThat(command)
        .anySatisfy(
            argument ->
                assertThat(argument)
                    .contains("ABC-42")
                    .contains("'shop'")
                    .contains("VAT goes to ledger"));
  }

  @Test
  void asksNothingAboutTwinRulesWhereFewerThanTwoAreWritten() {
    var answer = assistant.sameRules(List.of("VAT goes to ledger"));

    assertThat(answer.facts()).isEmpty();
    assertThat(runner.issued()).isEmpty();
  }

  @Test
  void namesTheKeptWordingAndTheDuplicateOfTwinRules() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"structured_output\":{\"kept\":\"VAT goes to ledger\",\"duplicate\":\"VAT sums"
                + " to ledger\"}}",
            ""));

    var answer = assistant.sameRules(List.of("VAT goes to ledger", "VAT sums to ledger"));

    assertThat(answer.facts()).contains(new RulePair("VAT goes to ledger", "VAT sums to ledger"));
  }
}
