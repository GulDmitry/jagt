package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.TerminalDriver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class KittyTerminalDriverTest {

    @Test
    void bindsCyrillicPasteAndCopySoPastingWorksOnTheRussianLayout() {
        var driver = new KittyTerminalDriver(mock(ProcessRunner.class), OrchestratorProperties.defaults(),
                mock(OsaScript.class), "kitty", "");

        assertThat(driver.platformOptions())
                .containsSequence("-o", "map=cmd+м paste_from_clipboard")
                .containsSequence("-o", "map=cmd+с copy_to_clipboard");
    }

    @Test
    void keepsTheLatinDefaultsInsteadOfRemappingCmdVWhichWouldDropKittysAsciiFallback() {
        var driver = new KittyTerminalDriver(mock(ProcessRunner.class), OrchestratorProperties.defaults(),
                mock(OsaScript.class), "kitty", "");

        assertThat(driver.platformOptions()).doesNotContain("map=cmd+v paste_from_clipboard",
                "map=cmd+c copy_to_clipboard");
    }

    @Test
    void raisesTheAppWithAppleScriptBecauseKittyCannotPutItselfInFrontOnCocoa() {
        OsaScript osaScript = mock(OsaScript.class);

        new KittyTerminalDriver(mock(ProcessRunner.class), OrchestratorProperties.defaults(), osaScript,
                "kitty", "").bringToFront();

        verify(osaScript).run("tell application \"kitty\" to activate");
    }

    @Test
    void opensNothingWhereTheHumanTurnedTheWindowOff() {
        ProcessRunner runner = mock(ProcessRunner.class);
        var driver = new KittyTerminalDriver(runner,
                OrchestratorProperties.defaults().withTmuxCommand("tmux").withOpenTerminalWindow(false),
                mock(OsaScript.class), "kitty", "");

        driver.openViewer("agents", "agents", Path.of("/work/tree"));

        verifyNoInteractions(runner);
    }

    @Test
    void startsTheViewerInstanceWhereNoneAnswers() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", ""));
        var driver = new KittyTerminalDriver(runner,
                OrchestratorProperties.defaults().withTmuxCommand("tmux").withOpenTerminalWindow(true),
                mock(OsaScript.class), "/k/kitty", "");

        driver.openViewer("agents", "agents", Path.of("/work/tree"));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner, times(2)).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getAllValues().get(1)).startsWith("/k/kitty", "--detach")
                .containsSequence("--", "tmux", "attach", "-t", "agents");
    }

    @Test
    void opensATabInTheViewerInstanceThatAnswers() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        var driver = new KittyTerminalDriver(runner,
                OrchestratorProperties.defaults().withTmuxCommand("tmux").withOpenTerminalWindow(true),
                mock(OsaScript.class), "/k/kitty", "");

        driver.openViewer("agents", "agents tab", Path.of("/work/tree"));

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner, times(2)).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getAllValues().get(1)).startsWith("/k/kitty", "@", "--to")
                .containsSequence("launch", "--type=tab", "--tab-title", "agents tab", "--cwd", "/work/tree")
                .containsSequence("--", "tmux", "attach", "-t", "agents");
    }

    @Test
    void opensNoSecondViewerWhileTheFirstIsStillAttaching() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        var driver = new KittyTerminalDriver(runner,
                OrchestratorProperties.defaults().withTmuxCommand("tmux").withOpenTerminalWindow(true),
                mock(OsaScript.class), "/k/kitty", "");

        driver.openViewer("agents", "agents", Path.of("/work/tree"));
        driver.openViewer("agents", "agents", Path.of("/work/tree"));

        verify(runner, times(2)).run(isNull(), any(Duration.class), anyList());
    }

    @Test
    void opensAViewerForAnotherSessionAtOnce() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        var driver = new KittyTerminalDriver(runner,
                OrchestratorProperties.defaults().withTmuxCommand("tmux").withOpenTerminalWindow(true),
                mock(OsaScript.class), "/k/kitty", "");

        driver.openViewer("agents", "agents", Path.of("/work/tree"));
        driver.openViewer("others", "others", Path.of("/work/tree"));

        verify(runner, times(4)).run(isNull(), any(Duration.class), anyList());
    }

    @Test
    void revealsNothingWhereNoViewerAnswers() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OsaScript osaScript = mock(OsaScript.class);
        when(runner.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", ""));
        var driver = new KittyTerminalDriver(runner, OrchestratorProperties.defaults(), osaScript, "/k/kitty", "");

        var revealed = driver.reveal("agents");

        assertThat(revealed).isEqualTo(TerminalDriver.Revealed.NOT_RUNNING);
        verify(osaScript, never()).run(any());
    }

    @Test
    void focusesTheViewerWindowAndRaisesTheAppWhereAViewerAnswers() {
        ProcessRunner runner = mock(ProcessRunner.class);
        OsaScript osaScript = mock(OsaScript.class);
        when(runner.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        var driver = new KittyTerminalDriver(runner, OrchestratorProperties.defaults(), osaScript, "/k/kitty", "");

        var revealed = driver.reveal("agents");

        assertThat(revealed).isEqualTo(TerminalDriver.Revealed.WINDOW);
        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner, times(2)).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getAllValues().get(1)).containsSequence("focus-window", "--match", "cmdline:tmux");
        verify(osaScript).run("tell application \"kitty\" to activate");
    }

    @Test
    void closesTheViewerByTheSocketOnlyItsInstanceHolds() {
        ProcessRunner runner = mock(ProcessRunner.class);
        var driver = new KittyTerminalDriver(runner, OrchestratorProperties.defaults(), mock(OsaScript.class),
                "/k/kitty", "");

        driver.closeViewerWindow("agents");

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(isNull(), any(Duration.class), command.capture());
        assertThat(command.getValue()).startsWith("pkill", "-f").last().asString().endsWith("jagt-kitty-agents");
    }
}
