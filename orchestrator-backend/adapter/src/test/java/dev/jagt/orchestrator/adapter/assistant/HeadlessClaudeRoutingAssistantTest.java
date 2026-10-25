package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RoutingQuestion;
import dev.jagt.orchestrator.task.RulePair;
import dev.jagt.orchestrator.task.TicketFacts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HeadlessClaudeRoutingAssistantTest {

    private final ProcessRunner runner = mock(ProcessRunner.class);
    private final HeadlessClaudeRoutingAssistant assistant = new HeadlessClaudeRoutingAssistant(
            new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                    mock(UsageTracker.class), mock(ReadOnlyTools.class)));

    @Test
    void asksNothingWhereTheQuestionNamesNoRepository() {
        var answer = assistant.routeProject(RoutingQuestion.defaults()
                .withItem(TicketFacts.defaults().withKey("ABC-42")));

        assertThat(answer.facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void asksNothingWhereThereIsNoQuestion() {
        var answer = assistant.routeProject(null);

        assertThat(answer.facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void readsTheRepositoryAndTheRuleOutOfTheStructuredAnswer() {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"project\":\"ledger\",\"rule\":\" invoice sums \"}}", ""));

        var answer = assistant.routeProject(RoutingQuestion.defaults()
                .withItem(TicketFacts.defaults().withKey("ABC-42").withTitle("VAT is wrong"))
                .withProjects(Map.of("ledger", "invoicing")));

        assertThat(answer.facts()).contains(new RoutingAnswer("ledger", "invoice sums"));
    }

    @Test
    void listsEachRepositoryTheRulesAndThePrecedentsInThePrompt() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"project\":\"ledger\"}}", ""));

        assistant.routeProject(RoutingQuestion.defaults()
                .withItem(TicketFacts.defaults().withKey("ABC-42").withTitle("VAT is wrong"))
                .withProjects(Map.of("ledger", "invoicing"))
                .withRules(List.of("VAT goes to ledger"))
                .withPrecedents(List.of("ABC-7 was done in ledger")));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anySatisfy(argument -> assertThat(argument)
                .contains("- ledger: invoicing")
                .contains("Rules this install has written, which outrank everything above:\nVAT goes to ledger\n")
                .contains("Items finished before, and the repository each was done in:\nABC-7 was done in ledger\n"));
    }

    @Test
    void leavesTheRuleAndPrecedentSectionsOutWhereThereAreNone() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"project\":\"ledger\"}}", ""));

        assistant.routeProject(RoutingQuestion.defaults()
                .withItem(TicketFacts.defaults().withKey("ABC-42").withTitle("VAT is wrong"))
                .withProjects(Map.of("ledger", "invoicing")));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).noneMatch(argument -> argument.contains("Rules this install has written")
                || argument.contains("Items finished before"));
    }

    @Test
    void asksNothingAboutAStaleRuleWhereNoRuleIsWritten() {
        var answer = assistant.staleRule("ABC-42", "ledger", List.of());

        assertThat(answer.facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void asksNothingAboutAStaleRuleWhereAnArgumentIsMissing() {
        var answer = assistant.staleRule(null, "ledger", List.of("VAT goes to ledger"));

        assertThat(answer.facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void namesTheWrittenRuleTheHandPlacementContradicted() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"rule\":\"VAT goes to ledger\"}}", ""));

        var answer = assistant.staleRule("ABC-42", "shop", List.of("VAT goes to ledger"));

        assertThat(answer.facts()).contains("VAT goes to ledger");
        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anySatisfy(argument -> assertThat(argument)
                .contains("ABC-42").contains("'shop'").contains("VAT goes to ledger"));
    }

    @Test
    void asksNothingAboutTwinRulesWhereFewerThanTwoAreWritten() {
        var answer = assistant.sameRules(List.of("VAT goes to ledger"));

        assertThat(answer.facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void namesTheKeptWordingAndTheDuplicateOfTwinRules() {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"kept\":\"VAT goes to ledger\",\"duplicate\":\"VAT sums to ledger\"}}", ""));

        var answer = assistant.sameRules(List.of("VAT goes to ledger", "VAT sums to ledger"));

        assertThat(answer.facts()).contains(new RulePair("VAT goes to ledger", "VAT sums to ledger"));
    }
}
