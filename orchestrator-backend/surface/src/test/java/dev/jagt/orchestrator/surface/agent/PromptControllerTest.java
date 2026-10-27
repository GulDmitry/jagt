package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.protocol.SessionHookReport;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptControllerTest {

    private final StateService state = mock(StateService.class);
    private final PromptGate gate = mock(PromptGate.class);
    private final AgentSessionController sessions = mock(AgentSessionController.class);
    private final TaskState task = TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build();
    private final SessionHookReport typed = new SessionHookReport(null, null, null, null, null, null,
            java.util.List.of(), "go on");

    @Test
    void reportsTheSessionWorkingWhenTheGateLetsThePromptThrough() {
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        when(gate.answer("ABC-1", task, "go on")).thenReturn("");
        when(sessions.report("working", "/wt/ABC-1-proj", typed)).thenReturn("");

        String answered = new PromptController(state, gate, sessions).prompt("/wt/ABC-1-proj", typed);

        assertThat(answered).isEmpty();
        verify(sessions).report("working", "/wt/ABC-1-proj", typed);
    }

    @Test
    void answersTheGatesBlockWithoutReportingTheSessionWorking() {
        when(state.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1", task)));
        when(gate.answer("ABC-1", task, "go on")).thenReturn("{\"decision\": \"block\"}");

        String answered = new PromptController(state, gate, sessions).prompt("/wt/ABC-1-proj", typed);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
        verify(sessions, never()).report(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void letsAPromptThroughFromARootSessionNoTaskOwns() {
        when(state.findByWorktree("/root")).thenReturn(Optional.empty());

        String answered = new PromptController(state, gate, sessions).prompt("/root", typed);

        assertThat(answered).isEmpty();
        verify(gate, never()).answer(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
