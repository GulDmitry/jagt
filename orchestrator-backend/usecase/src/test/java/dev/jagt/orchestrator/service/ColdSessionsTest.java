package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ColdSessionsTest {

    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);
    private final TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

    @Test
    void isColdWhenTheSessionsOwnRecordEndedItsTurnPastTheConfiguredCacheLifetime() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.turnEndedAt(Path.of("/wt"))).thenReturn(OptionalLong.of(5_000L));
        when(runtime.pastCacheLifetime(5_000L, Duration.ofHours(1))).thenReturn(true);

        assertThat(new ColdSessions(runtime, config).isCold(task)).isTrue();
    }

    @Test
    void isNotColdWhileTheSessionsRecordHoldsNoTurnEnd() {
        when(runtime.turnEndedAt(Path.of("/wt"))).thenReturn(OptionalLong.empty());

        assertThat(new ColdSessions(runtime, config).isCold(task)).isFalse();
    }

    @Test
    void isColdAfterJagtRestartedWithNoHookMarkKept() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.turnEndedAt(Path.of("/wt"))).thenReturn(OptionalLong.of(1_000L));
        when(runtime.pastCacheLifetime(1_000L, Duration.ofHours(1))).thenReturn(true);

        assertThat(new ColdSessions(runtime, config).isCold(task)).isTrue();
    }
}
