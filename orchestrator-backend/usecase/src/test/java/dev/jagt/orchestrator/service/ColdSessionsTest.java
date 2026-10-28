package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ColdSessionsTest {

    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);

    @Test
    void isColdWhenTheSessionsOwnRecordEndedItsTurnPastTheConfiguredCacheLifetime() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.turnEndedAt(Path.of("/logs/s.jsonl"))).thenReturn(OptionalLong.of(5_000L));
        when(runtime.pastCacheLifetime(5_000L, Duration.ofHours(1))).thenReturn(true);

        assertThat(new ColdSessions(runtime, config).isCold(Optional.of(Path.of("/logs/s.jsonl")))).isTrue();
    }

    @Test
    void isNotColdWhileTheSessionsRecordHoldsNoTurnEnd() {
        when(runtime.turnEndedAt(Path.of("/logs/s.jsonl"))).thenReturn(OptionalLong.empty());

        assertThat(new ColdSessions(runtime, config).isCold(Optional.of(Path.of("/logs/s.jsonl")))).isFalse();
    }

    @Test
    void isColdAfterJagtRestartedWithNoHookMarkKept() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.turnEndedAt(Path.of("/logs/s.jsonl"))).thenReturn(OptionalLong.of(1_000L));
        when(runtime.pastCacheLifetime(1_000L, Duration.ofHours(1))).thenReturn(true);

        assertThat(new ColdSessions(runtime, config).isCold(Optional.of(Path.of("/logs/s.jsonl")))).isTrue();
    }

    @Test
    void isNotColdForTheFreshSessionAColdPromptWentToWhoseLogDoesNotExistYet() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.turnEndedAt(Path.of("/logs/old.jsonl"))).thenReturn(OptionalLong.of(1_000L));
        when(runtime.pastCacheLifetime(1_000L, Duration.ofHours(1))).thenReturn(true);

        assertThat(new ColdSessions(runtime, config).isCold(Optional.of(Path.of("/logs/fresh.jsonl")))).isFalse();
    }

    @Test
    void isNotColdWhenThePromptNamesNoLog() {
        assertThat(new ColdSessions(runtime, config).isCold(Optional.empty())).isFalse();
    }
}
