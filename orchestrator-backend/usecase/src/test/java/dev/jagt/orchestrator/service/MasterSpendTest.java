package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterSpendTest {

    private final SessionLog sessionLog = mock(SessionLog.class);
    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    private MasterSpend spend(Path root) {
        return new MasterSpend(sessionLog, agentRuntime,
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));
    }

    @Test
    void countsNothingWhileTheRuntimeKeepsNoLogItCanFind(@TempDir Path root) {
        when(agentRuntime.sessionLogOf(any())).thenReturn(Optional.empty());
        MasterSpend spend = spend(root);

        spend.advance();

        assertThat(spend.total().total()).isZero();
    }

    @Test
    void addsWhatTheSessionAppendedSinceTheLastLook(@TempDir Path root) throws Exception {
        Path log = Files.writeString(root.resolve("session.jsonl"), "{}\n");
        when(agentRuntime.sessionLogOf(any())).thenReturn(Optional.of(log));
        when(sessionLog.spent(any(), anyLong(), anyLong()))
                .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(100, 0, 20, 0.5), 3));
        MasterSpend spend = spend(root);

        spend.advance();

        assertThat(spend.total().total()).isEqualTo(120);
        assertThat(spend.total().costUsd()).isEqualTo(0.5);
    }

    @Test
    void countsAnotherSessionsLogFromItsOwnStartWhileKeepingTheTotal(@TempDir Path root) throws Exception {
        Path first = Files.writeString(root.resolve("one.jsonl"), "{}\n");
        Path second = Files.writeString(root.resolve("two.jsonl"), "{}\n");
        when(sessionLog.spent(any(), anyLong(), anyLong()))
                .thenReturn(new SessionLog.Spent(TokenUsage.ofCall(10, 0, 0, 0.1), 3));
        MasterSpend spend = spend(root);

        when(agentRuntime.sessionLogOf(any())).thenReturn(Optional.of(first));
        spend.advance();
        when(agentRuntime.sessionLogOf(any())).thenReturn(Optional.of(second));
        spend.advance();

        assertThat(spend.total().total()).isEqualTo(20);
    }
}
