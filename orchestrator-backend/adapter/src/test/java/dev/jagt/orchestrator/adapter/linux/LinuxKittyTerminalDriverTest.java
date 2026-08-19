package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LinuxKittyTerminalDriverTest {

    @Test
    void refusesToStartWhenNothingWouldShowTheAgentsSessions() {
        var driver = new LinuxKittyTerminalDriver(mock(ProcessRunner.class),
                OrchestratorProperties.defaults(), "no-such-kitty", "");

        assertThat(driver.problems()).singleElement(STRING)
                .contains("orchestrator.kitty-command", "no-such-kitty");
    }

    /**
     * The {@code cmd+} mappings exist because Cocoa matches a key-equivalent by produced character; on Linux
     * kitty's own ascii fallback covers a non-Latin layout — and {@code cmd} is not even a modifier here.
     */
    @Test
    void addsNoneOfTheMacOnlyKeyboardWorkaroundToTheViewer() {
        var driver = new LinuxKittyTerminalDriver(mock(ProcessRunner.class),
                OrchestratorProperties.defaults(), "kitty", "");

        assertThat(driver.platformOptions()).isEmpty();
    }

    /**
     * Deliberately a no-op: {@code reveal} already asked kitty to focus the window, and stacking is the window
     * manager's call. Pinned so nobody "fixes" it with a wmctrl/xdotool dependency that works on one desktop.
     */
    @Test
    void leavesRaisingTheWindowToTheWindowManager() {
        ProcessRunner runner = mock(ProcessRunner.class);

        assertThatCode(() -> new LinuxKittyTerminalDriver(runner, OrchestratorProperties.defaults(),
                "kitty", "").bringToFront()).doesNotThrowAnyException();
        verifyNoInteractions(runner);
    }
}
