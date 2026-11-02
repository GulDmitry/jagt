package dev.jagt.orchestrator.capability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FocusCapabilityTest {

    private final TaskViewer viewer = mock(TaskViewer.class);

    @Test
    void answersWithWhatTheViewerSaidItDid() {
        when(viewer.focus("ABC-1")).thenReturn("Focused tab 'ABC-1'");

        assertThat(new FocusCapability(viewer).run("ABC-1").message()).isEqualTo("Focused tab 'ABC-1'");
    }
}
