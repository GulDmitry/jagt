package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnEndsTest {

    private final SessionProbe probe = mock(SessionProbe.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);

    @Test
    void sendsOnATurnThatEndsWithTheAgentsMoveUnreported() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        when(runtime.refusedTurnEnd(contains("update_agent_status"))).thenReturn("{\"decision\": \"block\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000L).build();

        String answered = new TurnEnds(probe, runtime, config).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void letsATurnEndThatReportedAfterItBegan() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(3_000L).build();

        String answered = new TurnEnds(probe, runtime, config).answer("ABC-1", task, false, false);

        assertThat(answered).isEmpty();
    }

    @Test
    void tellsTheHumanARoundHandedBackIsWithTheMaster() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(runtime.toldTheHuman("→ with the Master for review · its verdict arrives here"))
                .thenReturn("{\"systemMessage\": \"with the Master\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(3_000L).build();

        String answered = new TurnEnds(probe, runtime, config).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"systemMessage\": \"with the Master\"}");
    }

    @Test
    void saysNothingAtAHandedBackRoundNoMasterReads() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(3_000L).build();

        String answered = new TurnEnds(probe, runtime, config).answer("ABC-1", task, false, false);

        assertThat(answered).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void letsATurnEndThatWasAlreadySentOnOrWaitsOnBackgroundWork(boolean sentOn, boolean paused) {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000L).build();

        String answered = new TurnEnds(probe, runtime, config).answer("ABC-1", task, sentOn, paused);

        assertThat(answered).isEmpty();
    }

    @Test
    void tellsTheHumanARoundBeingVerifiedIsVerifiedBeforeTheMasterReadsIt() {
        when(probe.turnStartedAt("ABC-1")).thenReturn(2_000L);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(runtime.toldTheHuman("→ verification runs first · the Master reads the round once it passes"))
                .thenReturn("{\"systemMessage\": \"verification first\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1")
                .lastActiveTimestamp(3_000L).build();

        String answered = new TurnEnds(probe, runtime, config).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"systemMessage\": \"verification first\"}");
    }
}
