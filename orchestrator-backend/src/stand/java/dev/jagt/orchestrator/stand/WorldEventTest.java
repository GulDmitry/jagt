package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class WorldEventTest {

    @Test
    void jagtIsRestartedOnTheFirstRecognisedRelayOfAPendingStep() {
        StandWorld world = mock(StandWorld.class);
        when(world.relaysSeen()).thenReturn(1);

        boolean fired = WorldEvent.restart().fire(world);

        assertThat(fired).isTrue();
        verify(world).restart();
    }

    @Test
    void jagtIsNotRestartedBeforeAnyRelay() {
        StandWorld world = mock(StandWorld.class);
        when(world.relaysSeen()).thenReturn(0);

        boolean fired = WorldEvent.restart().fire(world);

        assertThat(fired).isFalse();
    }

    @Test
    void theHumanTypesOnTheFirstRecognisedRelay() {
        StandWorld world = mock(StandWorld.class);
        when(world.relaysSeen()).thenReturn(1);

        boolean fired = WorldEvent.humanLine("wait").fire(world);

        assertThat(fired).isTrue();
        verify(world).say("wait");
    }
}
