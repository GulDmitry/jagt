package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaffeinateTest {

    private final Processes processes = mock(Processes.class);
    private final Process caffeinate = mock(Process.class);

    @Test
    void holdsTheAssertionForAsLongAsThisProcessLives() {
        when(processes.runDetached(isNull(), any())).thenReturn(caffeinate);

        new Caffeinate(processes).hold();

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).runDetached(isNull(), command.capture());
        assertThat(command.getValue()).endsWith("-i", "-w", Long.toString(ProcessHandle.current().pid()));
        assertThat(command.getValue().getFirst()).endsWith("caffeinate");
    }

    @Test
    void spawnsOneAssertionWhileItLives() {
        when(processes.runDetached(isNull(), any())).thenReturn(caffeinate);
        when(caffeinate.isAlive()).thenReturn(true);
        Caffeinate keepAwake = new Caffeinate(processes);

        keepAwake.hold();
        keepAwake.hold();

        verify(processes, times(1)).runDetached(isNull(), any());
    }

    @Test
    void releasingEndsTheAssertion() {
        when(processes.runDetached(isNull(), any())).thenReturn(caffeinate);
        Caffeinate keepAwake = new Caffeinate(processes);
        keepAwake.hold();

        keepAwake.release();

        verify(caffeinate).destroy();
    }
}
