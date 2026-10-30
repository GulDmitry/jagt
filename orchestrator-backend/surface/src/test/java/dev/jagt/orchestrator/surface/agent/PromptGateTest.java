package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ColdPrompts;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptGateTest {

    private final ColdPrompts coldPrompts = mock(ColdPrompts.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);
    private final TaskState idle = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build().withSession(
            Session.defaults().withMovedAt(1_000)
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null)));

    PromptGateTest() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    @Test
    void blocksAColdPromptOnlyAfterItWasRelayedToAFreshSession() {
        when(runtime.pastCacheLifetime(5_000L, Duration.ofHours(1))).thenReturn(true);
        when(runtime.blockedPrompt(anyString())).thenReturn("{\"decision\": \"block\"}");

        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", idle, "now handle empty files");

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
        InOrder order = inOrder(coldPrompts, runtime);
        order.verify(coldPrompts).relay("ABC-1", idle, "now handle empty files");
        order.verify(runtime).blockedPrompt(anyString());
    }

    @Test
    void callsASessionColdFromTheStampTheRelayUses() {
        when(runtime.pastCacheLifetime(1_000L, Duration.ofHours(1))).thenReturn(true);

        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", idle, "go on");

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), any(), anyString());
    }

    @Test
    void letsAPromptThroughWhileTheTurnIsOpen() {
        TaskState working = idle.withSession(Session.defaults().withMovedAt(5_000));
        when(runtime.pastCacheLifetime(anyLong(), any())).thenReturn(true);

        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", working, "go on");

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), any(), anyString());
    }

    @Test
    void letsAPromptThroughWhenTheSessionHasSignedNothing() {
        TaskState unsigned = idle.withSession(Session.defaults());
        when(runtime.pastCacheLifetime(anyLong(), any())).thenReturn(true);

        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", unsigned, "go on");

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), any(), anyString());
    }

    @Test
    void letsAColdPromptThroughWhenItCouldNotBeRelayed() {
        when(runtime.pastCacheLifetime(5_000L, Duration.ofHours(1))).thenReturn(true);
        doThrow(new IllegalStateException("tmux down")).when(coldPrompts).relay("ABC-1", idle, "go on");

        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", idle, "go on");

        assertThat(answered).isEmpty();
        verify(runtime, never()).blockedPrompt(anyString());
    }

    @Test
    void letsAPromptThroughWhenNoTaskOwnsTheWorktree() {
        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", null, "go on");

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), any(), anyString());
    }

    @Test
    void letsAPromptThroughWhileTheSessionIsWithinItsCacheLifetime() {
        when(runtime.pastCacheLifetime(5_000L, Duration.ofHours(1))).thenReturn(false);

        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", idle, "go on");

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/compact", "/clear", "look at [Image #1] please"})
    void letsASlashCommandOrAPastedImageThroughEvenInAColdSession(String typed) {
        when(runtime.pastCacheLifetime(5_000L, Duration.ofHours(1))).thenReturn(true);

        String answered = new PromptGate(coldPrompts, runtime, config).answer("ABC-1", idle, typed);

        assertThat(answered).isEmpty();
        verify(coldPrompts, never()).relay(anyString(), any(), anyString());
    }
}
