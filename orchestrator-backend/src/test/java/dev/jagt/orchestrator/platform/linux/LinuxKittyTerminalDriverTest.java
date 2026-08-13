package dev.jagt.orchestrator.platform.linux;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinuxKittyTerminalDriverTest {

    @Test
    void launchesKittyWithoutTheMacOnlyKeyboardWorkaround() {
        // The cmd+ mappings exist because Cocoa matches a key-equivalent by produced character; on Linux
        // kitty's own ascii fallback covers a non-Latin layout — and `cmd` is not even a modifier here.
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), any(Duration.class), any()))
                .thenReturn(new ProcessRunner.ProcessResult(1, "", ""))        // no instance yet
                .thenReturn(new ProcessRunner.ProcessResult(0, "", ""));
        var driver = new LinuxKittyTerminalDriver(runner, OrchestratorProperties.defaults()
                .withOpenWarpWindow(true).withTmuxCommand("tmux"), "kitty", "");

        driver.openViewer("jagt", "jagt", Path.of("/tmp/wt"));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner, atLeast(2)).run(any(), any(Duration.class), command.capture());
        List<String> launch = command.getAllValues().getLast();
        assertThat(launch).startsWith("kitty", "--detach")
                .containsSequence("-o", "allow_remote_control=yes")
                .containsSequence("--", "tmux", "attach", "-t", "jagt");
        assertThat(launch).noneMatch(argument -> argument.startsWith("map=cmd+"));
    }

    @Test
    void leavesRaisingTheWindowToTheWindowManager() {
        // Deliberately a no-op: `reveal` already asked kitty to focus the window, and stacking is the WM's
        // call. Pinned so nobody "fixes" it later with a wmctrl/xdotool dependency that works on one desktop.
        ProcessRunner runner = mock(ProcessRunner.class);

        assertThatCode(() -> new LinuxKittyTerminalDriver(runner, OrchestratorProperties.defaults(),
                "kitty", "").bringToFront()).doesNotThrowAnyException();
        verifyNoInteractions(runner);
    }
}
