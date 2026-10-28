package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ColdPrompts;
import dev.jagt.orchestrator.service.ColdSessions;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptGateTest {

    private final ColdSessions cold = mock(ColdSessions.class);
    private final ColdPrompts coldPrompts = mock(ColdPrompts.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final Optional<Path> log = Optional.of(Path.of("/logs/s.jsonl"));
    private final TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

    @Test
    void blocksAColdPromptOnlyAfterItWasRelayedToAFreshSession() {
        when(cold.isCold(log)).thenReturn(true);
        when(runtime.blockedPrompt(anyString())).thenReturn("{\"decision\": \"block\"}");

        String answered = new PromptGate(cold, coldPrompts, runtime).answer("ABC-1", task, "now handle empty files", log);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
        InOrder order = inOrder(coldPrompts, runtime);
        order.verify(coldPrompts).relay("ABC-1", task, "now handle empty files");
        order.verify(runtime).blockedPrompt(anyString());
    }

    @Test
    void letsAColdPromptThroughWhenItCouldNotBeRelayed() {
        when(cold.isCold(log)).thenReturn(true);
        doThrow(new IllegalStateException("tmux down")).when(coldPrompts).relay("ABC-1", task, "go on");

        String answered = new PromptGate(cold, coldPrompts, runtime).answer("ABC-1", task, "go on", log);

        assertThat(answered).isEmpty();
        verify(runtime, never()).blockedPrompt(anyString());
    }

    @Test
    void letsAPromptThroughWhenNoTaskOwnsTheWorktree() {
        String answered = new PromptGate(cold, coldPrompts, runtime).answer("ABC-1", null, "go on", log);

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void letsAPromptThroughWhileTheSessionIsWithinItsCacheLifetimeOrWorking() {
        when(cold.isCold(log)).thenReturn(false);

        String answered = new PromptGate(cold, coldPrompts, runtime).answer("ABC-1", task, "go on", log);

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/compact", "/clear", "look at [Image #1] please"})
    void letsASlashCommandOrAPastedImageThroughEvenInAColdSession(String typed) {
        when(cold.isCold(log)).thenReturn(true);

        String answered = new PromptGate(cold, coldPrompts, runtime).answer("ABC-1", task, typed, log);

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }
}
