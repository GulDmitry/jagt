package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ColdSessionsTest {

    private final SessionProbe probe = mock(SessionProbe.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);

    @Test
    void isColdWhenTheSessionStandsAtAPromptPastTheConfiguredCacheLifetime() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(probe.atAPromptSince("ABC-1")).thenReturn(OptionalLong.of(5_000L));
        when(runtime.pastCacheLifetime(5_000L, Duration.ofHours(1))).thenReturn(true);

        assertThat(new ColdSessions(probe, runtime, config).isCold("ABC-1")).isTrue();
    }

    @Test
    void isNotColdWhileTheSessionWorksOrNothingWasReported() {
        when(probe.atAPromptSince("ABC-1")).thenReturn(OptionalLong.empty());

        assertThat(new ColdSessions(probe, runtime, config).isCold("ABC-1")).isFalse();
    }
}
