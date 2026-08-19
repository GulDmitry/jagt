package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Processes;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeadlessClaudeAssistantTest {

    @Test
    void liftsThePermissionGateSoTheHeadlessReadCanCallMcpTools() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty().withPermissionMode("bypassPermissions"));

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--permission-mode", "bypassPermissions");
    }

    @Test
    void scopesTheBypassToTheConfiguredMcpServersInsteadOfLiftingItWholesale() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty().withPermissionMode("bypassPermissions")
                        .withAllowedTools(List.of("mcp__acme_jira", "mcp__acme_gitlab")));

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).endsWith("--allowedTools", "mcp__acme_jira", "mcp__acme_gitlab");
        assertThat(command.getValue()).doesNotContain("--permission-mode");
    }

    @Test
    void loadsOnlyTheDeclaredMcpServersInsteadOfWhateverTheHumanHasInstalled() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty()
                        .withMcpConfig("{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"y\"}}}"));

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--mcp-config",
                "{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"y\"}}}");
        assertThat(command.getValue()).contains("--strict-mcp-config");
        assertThat(command.getValue()).containsSequence("--setting-sources", "user,project,local");
    }

    @Test
    void inheritsTheHumansOwnMcpServersWhenTheInstallDeclaresNone() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties, AssistantProperties.empty());

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--setting-sources", "user,project,local");
        assertThat(command.getValue()).doesNotContain("--strict-mcp-config");
    }

    @Test
    void runsTheConfiguredModelInsteadOfWhateverTheHumansDefaultCostsToday() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty().withModel("haiku"));

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--model", "haiku");
    }

    @Test
    void inheritsTheHumansOwnModelWhenNoneIsConfigured() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty().withModel(""));

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).doesNotContain("--model");
    }

    @Test
    void asksForTheJsonEnvelopeSoEveryCallCarriesItsOwnCost() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty());

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--output-format", "json");
    }

    @Test
    void readsTheTicketOutOfTheEnvelopesStructuredOutput() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,"total_cost_usd":0.05,
                 "usage":{"input_tokens":10,"cache_creation_input_tokens":24000,\
                "cache_read_input_tokens":0,"output_tokens":170},
                 "structured_output":{"exists":true,"key":"ABC-42","title":"Widget layout is off",\
                "trackerProject":"ABC","labels":["backend"],"url":"https://tracker/ABC-42"}}""", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty());

        var facts = assistant.readTicket("ABC-42").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().key()).isEqualTo("ABC-42");
        assertThat(facts.get().title()).isEqualTo("Widget layout is off");
        assertThat(facts.get().labels()).containsExactly("backend");
    }

    @Test
    void fallsBackToTheResultStringWhenTheEnvelopeCarriesNoParsedOutput() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "result":"{\\"exists\\":true,\\"key\\":\\"ABC-7\\",\\"title\\":\\"Late invoice mail\\",\
                \\"trackerProject\\":\\"ABC\\",\\"labels\\":[],\\"url\\":\\"\\"}"}""", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty());

        var facts = assistant.readTicket("ABC-7").facts();

        assertThat(facts).isPresent();
        assertThat(facts.get().key()).isEqualTo("ABC-7");
        assertThat(facts.get().title()).isEqualTo("Late invoice mail");
    }

    @Test
    void reportsTheCostOfACallTheModelAnsweredWithAnError() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":true,"total_cost_usd":0.12,
                 "usage":{"input_tokens":5,"cache_creation_input_tokens":25000,\
                "cache_read_input_tokens":0,"output_tokens":40},
                 "result":"the tracker MCP is not available"}""", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                AssistantProperties.empty());

        var answer = assistant.readReview("https://host/mr/9");

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.usage()).isEqualTo(TokenUsage.ofCall(25_005, 0, 40, 0.12));
    }

    @Test
    void countsCacheWritesAsFreshInputAndCacheReadsApart() {
        var envelope = new JsonMapper().readTree("""
                {"total_cost_usd":0.4,"usage":{"input_tokens":4,"cache_creation_input_tokens":38441,
                 "cache_read_input_tokens":38395,"output_tokens":60}}""");

        TokenUsage usage = HeadlessClaudeAssistant.usageOf(envelope);

        assertThat(usage.calls()).isEqualTo(1);
        assertThat(usage.inputTokens()).isEqualTo(38445);
        assertThat(usage.cachedInputTokens()).isEqualTo(38395);
        assertThat(usage.outputTokens()).isEqualTo(60);
        assertThat(usage.costUsd()).isEqualTo(0.4);
    }

    @Test
    void reportsNoUsageRatherThanZerosWhenTheOutputCarriesNoUsageBlock() {
        var envelope = new JsonMapper().readTree("{\"result\":\"{}\"}");

        assertThat(HeadlessClaudeAssistant.usageOf(envelope)).isEqualTo(TokenUsage.NONE);
    }
}
