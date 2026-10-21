package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.adapter.KittyOptions;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LinuxKittyTerminalDriverTest {

    @Test
    void refusesToStartWhenNothingWouldShowTheAgentsSessions() {
        var driver = new LinuxKittyTerminalDriver(mock(ProcessRunner.class),
                OrchestratorProperties.defaults(), new KittyOptions("no-such-kitty", ""), Clock.systemUTC());

        assertThat(driver.problems()).extracting(Problem::key).containsExactly("orchestrator.kitty-command");
    }

    @Test
    void addsNoneOfTheMacOnlyKeyboardWorkaroundToTheViewer() {
        var driver = new LinuxKittyTerminalDriver(mock(ProcessRunner.class),
                OrchestratorProperties.defaults(), new KittyOptions("kitty", ""), Clock.systemUTC());

        assertThat(driver.platformOptions()).isEmpty();
    }

    @Test
    void leavesRaisingTheWindowToTheWindowManager() {
        ProcessRunner runner = mock(ProcessRunner.class);

        assertThatCode(() -> new LinuxKittyTerminalDriver(runner, OrchestratorProperties.defaults(),
                new KittyOptions("kitty", ""), Clock.systemUTC()).bringToFront()).doesNotThrowAnyException();
        verifyNoInteractions(runner);
    }
}
