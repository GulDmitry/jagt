package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.port.Processes;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What is macOS-specific about the kitty viewer; the OS-neutral argv is covered by the base-class test. */
class KittyTerminalDriverTest {

    private static List<String> launchCommand() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), any(Duration.class), any()))
                .thenReturn(new Processes.Result(1, "", ""))        // no instance yet
                .thenReturn(new Processes.Result(0, "", ""));
        new KittyTerminalDriver(runner, OrchestratorProperties.defaults()
                .withOpenWarpWindow(true).withTmuxCommand("tmux"), mock(OsaScript.class), "kitty", "")
                .openViewer("agents", "agents", Path.of("/work/tree"));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner, atLeast(2)).run(any(), any(Duration.class), command.capture());
        return command.getAllValues().getLast();
    }

    @Test
    void bindsCyrillicPasteAndCopySoPastingWorksOnTheRussianLayout() {
        assertThat(launchCommand()).containsSequence("-o", "map=cmd+м paste_from_clipboard")
                .containsSequence("-o", "map=cmd+с copy_to_clipboard");
    }

    @Test
    void keepsTheLatinDefaultsInsteadOfRemappingCmdVWhichWouldDropKittysAsciiFallback() {
        assertThat(launchCommand()).doesNotContain("map=cmd+v paste_from_clipboard",
                "map=cmd+c copy_to_clipboard");
    }

    @Test
    void raisesTheAppWithAppleScriptBecauseKittyCannotPutItselfInFrontOnCocoa() {
        OsaScript osaScript = mock(OsaScript.class);

        new KittyTerminalDriver(mock(ProcessRunner.class), OrchestratorProperties.defaults(), osaScript,
                "kitty", "").bringToFront();

        verify(osaScript).run("tell application \"kitty\" to activate");
    }
}
