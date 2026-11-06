package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemdInhibitTest {

    @Test
    void inhibitsIdleSleepForAsLongAsThisProcessLives() {
        Processes processes = mock(Processes.class);

        new SystemdInhibit(processes).hold();

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(processes).runDetached(isNull(), command.capture());
        assertThat(command.getValue().getFirst()).endsWith("systemd-inhibit");
        assertThat(command.getValue()).contains("--what=idle:sleep", "--mode=block")
                .endsWith("--pid=" + ProcessHandle.current().pid(), "-f", "/dev/null");
    }
}
