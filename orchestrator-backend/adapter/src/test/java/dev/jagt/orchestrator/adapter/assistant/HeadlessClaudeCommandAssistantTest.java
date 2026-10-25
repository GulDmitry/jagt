package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.port.Processes;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HeadlessClaudeCommandAssistantTest {

    private final ProcessRunner runner = mock(ProcessRunner.class);
    private final HeadlessClaudeCommandAssistant assistant = new HeadlessClaudeCommandAssistant(
            new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                    mock(UsageTracker.class), mock(ReadOnlyTools.class)));
    private final CommandAssistant.Menu menu = new CommandAssistant.Menu("ship <task>\nresume <url>",
            List.of("ship", "resume", "none"), List.of("ABC-5"));

    @Test
    void asksNothingWhereTheRequestIsBlank() {
        var answer = assistant.mapCommand("  ", menu);

        assertThat(answer.facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void asksNothingWhereThereIsNoRequest() {
        var answer = assistant.mapCommand(null, menu);

        assertThat(answer.facts()).isEmpty();
        verifyNoInteractions(runner);
    }

    @Test
    void proposesTheCommandTheTaskTheTicketAndTheReasonTheAnswerNames() {
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"command\":\"ship\",\"task\":\"ABC-5\",\"ticket\":\"\","
                        + "\"reason\":\"asked to send it out\"}}", ""));

        var answer = assistant.mapCommand("send ABC-5 out for review", menu);

        assertThat(answer.facts()).contains(new CommandAssistant.CommandProposal("ship", "ABC-5", "",
                "asked to send it out"));
    }

    @Test
    void putsTheRequestAndTheMenuInThePrompt() {
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"command\":\"none\"}}", ""));

        assistant.mapCommand("send ABC-5 out for review", menu);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).anySatisfy(argument -> assertThat(argument)
                .contains("<request>send ABC-5 out for review</request>").contains("ship <task>\nresume <url>"));
    }
}
