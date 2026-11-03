package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NotesDue;
import dev.jagt.orchestrator.service.TurnTree;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TurnEndsTreeTest {

    private final TurnTree turnTree = mock(TurnTree.class);
    private final NotesDue notes = new NotesDue(Clock.fixed(Instant.ofEpochMilli(10_000_000_000L), ZoneOffset.UTC));
    private final TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L)).build();

    @Test
    void aTurnThatChangedTheTreeAtDeployedAndReportedNothingIsRefusedOnce() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(turnTree.changedSinceLastEnd("ABC-1", task)).thenReturn(true);
        when(runtime.refusedTurnEnd(contains("the tree changed"))).thenReturn("{\"decision\": \"block\"}");

        String answered = new TurnEnds(runtime, null, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void aReportedTurnEndRecordsTheTreeTheNextTurnIsMeasuredFrom() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TaskState reported = TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L)).build();

        new TurnEnds(mock(AgentRuntime.class), config, notes, turnTree).answer("ABC-1", reported, false, false);

        verify(turnTree).changedSinceLastEnd("ABC-1", reported);
    }

    @Test
    void aTurnEndTheRefusalSentOnRecordsTheTreeTheNextTurnIsMeasuredFrom() {
        new TurnEnds(mock(AgentRuntime.class), null, notes, turnTree).answer("ABC-1", task, true, false);

        verify(turnTree).changedSinceLastEnd("ABC-1", task);
    }

    @Test
    void aDeployCheckThatChangedNothingEndsItsTurn() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(turnTree.changedSinceLastEnd("ABC-1", task)).thenReturn(false);

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEmpty();
    }
}
