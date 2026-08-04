package dev.jagt.orchestrator.assistant;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ProcessRunner;
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

class HeadlessClaudeAssistantTest {

    @Test
    void liftsThePermissionGateSoTheHeadlessReadCanCallMcpTools() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OrchestratorProperties properties = mock(OrchestratorProperties.class);
        when(properties.claudeCommand()).thenReturn("claude");
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new ProcessRunner.ProcessResult(0, "{\"exists\":false,\"key\":\"\",\"title\":\"\",\"trackerProject\":\"\",\"labels\":[]}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                new AssistantProperties("user,project,local", null, "bypassPermissions", List.of()));

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
                .thenReturn(new ProcessRunner.ProcessResult(0, "{\"exists\":false,\"key\":\"\",\"title\":\"\",\"trackerProject\":\"\",\"labels\":[]}", ""));
        var assistant = new HeadlessClaudeAssistant(runner, properties,
                new AssistantProperties("user,project,local", null, "bypassPermissions", List.of("mcp__acme_jira", "mcp__acme_gitlab")));

        assistant.readTicket("ABC-42");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).endsWith("--allowedTools", "mcp__acme_jira", "mcp__acme_gitlab");
        assertThat(command.getValue()).doesNotContain("--permission-mode");
    }
}
