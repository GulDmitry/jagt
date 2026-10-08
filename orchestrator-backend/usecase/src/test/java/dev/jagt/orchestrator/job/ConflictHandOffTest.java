package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConflictHandOffTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final CommandService commands = mock(CommandService.class);

    @Test
    void handsAConflictToTheSession() {
        new ConflictHandOff(sessions, commands).handle("ABC-42",
                new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"), false));

        verify(sessions).relayIfChanged(eq("ABC-42"), contains("/src/ABC-42-deploy"));
        verify(commands, never()).execute(anyString(), any());
    }

    @Test
    void asksOncePerConflictEvenWhereAnotherRelayOverwroteTheAsk() {
        ConflictHandOff handOff = new ConflictHandOff(sessions, commands);
        DeployConflicts.WaitingConflict conflict = new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"), false);

        handOff.handle("ABC-42", conflict);
        handOff.handle("ABC-42", conflict);

        verify(sessions, times(1)).relayIfChanged(eq("ABC-42"), anyString());
    }

    @Test
    void finishesTheDeployOnceTheResolutionIsStagedInFull() {
        new ConflictHandOff(sessions, commands).handle("ABC-42",
                new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"), true));

        verify(commands).execute("ABC-42", TaskAction.DEPLOY);
    }
}
