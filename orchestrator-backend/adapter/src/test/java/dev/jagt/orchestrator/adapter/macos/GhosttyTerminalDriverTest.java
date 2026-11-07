package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.TerminalDriver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GhosttyTerminalDriverTest {

    @Test
    void opensNoGhosttyWindowWhereTheHumanTurnedTheWindowOff() {
        OsaScript osaScript = mock(OsaScript.class);
        var driver = new GhosttyTerminalDriver(
                OrchestratorProperties.defaults().withOpenTerminalWindow(false),
                osaScript, mock(Host.class), Clock.systemUTC());

        driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

        verifyNoInteractions(osaScript);
    }

    @Test
    void opensTheViewerAsATabAttachedToTmuxTitledWithTheViewerTitle() {
        OsaScript osaScript = mock(OsaScript.class);
        var driver = new GhosttyTerminalDriver(
                OrchestratorProperties.defaults().withOpenTerminalWindow(true),
                osaScript, mock(Host.class), Clock.systemUTC());

        driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents \"main\"", Path.of("/work/tree"));

        ArgumentCaptor<String> script = ArgumentCaptor.captor();
        verify(osaScript).run(script.capture());
        assertThat(script.getValue())
                .contains(" attach -t agents\"")
                .contains("initial working directory:\"/work/tree\"")
                .contains("new tab in hostWindow")
                .contains("name override of newTab to \"agents \\\"main\\\"\"");
    }

    @Test
    void runsTheAttachCommandItIsGivenWordForWord() {
        OsaScript osaScript = mock(OsaScript.class);
        var driver = new GhosttyTerminalDriver(
                OrchestratorProperties.defaults().withOpenTerminalWindow(true),
                osaScript, mock(Host.class), Clock.systemUTC());

        driver.openViewer(List.of("tmux", "-L", "jagt-sock", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

        ArgumentCaptor<String> script = ArgumentCaptor.captor();
        verify(osaScript).run(script.capture());
        assertThat(script.getValue()).contains(" -L jagt-sock attach -t agents\"");
    }

    @Test
    void opensNoSecondViewerWhileTheFirstIsStillAttaching() {
        OsaScript osaScript = mock(OsaScript.class);
        var driver = new GhosttyTerminalDriver(
                OrchestratorProperties.defaults().withOpenTerminalWindow(true),
                osaScript, mock(Host.class), Clock.systemUTC());

        driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));
        driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

        verify(osaScript).run(anyString());
    }

    @Test
    void neverThrowsWhereGhosttyRefusesTheScript() {
        OsaScript osaScript = mock(OsaScript.class);
        doThrow(new IllegalStateException("osascript failed")).when(osaScript).run(anyString());
        var driver = new GhosttyTerminalDriver(
                OrchestratorProperties.defaults().withOpenTerminalWindow(true),
                osaScript, mock(Host.class), Clock.systemUTC());

        driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

        verify(osaScript).run(anyString());
    }

    @Test
    void revealsTheWindowWhereAWindowHoldsTheTitle() {
        OsaScript osaScript = mock(OsaScript.class);
        when(osaScript.answer(anyString())).thenReturn("window");
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), osaScript, mock(Host.class),
                Clock.systemUTC());

        assertThat(driver.reveal("agents")).isEqualTo(TerminalDriver.Revealed.WINDOW);
    }

    @Test
    void reportsNotRunningWhereNoWindowAnswers() {
        OsaScript osaScript = mock(OsaScript.class);
        when(osaScript.answer(anyString())).thenReturn("none");
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), osaScript, mock(Host.class),
                Clock.systemUTC());

        assertThat(driver.reveal("agents")).isEqualTo(TerminalDriver.Revealed.NOT_RUNNING);
    }

    @Test
    void closesTheWindowsHoldingTheTitleWithoutLaunchingGhostty() {
        OsaScript osaScript = mock(OsaScript.class);
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), osaScript, mock(Host.class),
                Clock.systemUTC());

        driver.closeViewerWindow("agents");

        ArgumentCaptor<String> script = ArgumentCaptor.captor();
        verify(osaScript).run(script.capture());
        assertThat(script.getValue()).startsWith("if application \"Ghostty\" is not running then return")
                .contains("close window w");
    }

    @Test
    void refusesGhosttyOnLinuxBecauseItHasNoAddressableTabsThere() {
        Host host = mock(Host.class);
        when(host.osName()).thenReturn("Linux");
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), mock(OsaScript.class), host,
                Clock.systemUTC());

        List<Problem> problems = driver.problems();

        assertThat(problems).extracting(Problem::key).containsExactly("orchestrator.terminal");
        assertThat(problems.getFirst().text()).contains("Set it to 'kitty'");
    }

    @Test
    void refusesGhosttyThatIsNotInstalledNamingTheInstall() {
        Host host = mock(Host.class);
        when(host.osName()).thenReturn("Mac OS X");
        OsaScript osaScript = mock(OsaScript.class);
        doThrow(new IllegalStateException("osascript failed")).when(osaScript).run(anyString());
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), osaScript, host, Clock.systemUTC());

        assertThat(driver.problems().getFirst().text()).contains("https://ghostty.org");
    }

    @Test
    void acceptsGhosttyInstalledOnMacos() {
        Host host = mock(Host.class);
        when(host.osName()).thenReturn("Mac OS X");
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), mock(OsaScript.class), host,
                Clock.systemUTC());

        assertThat(driver.problems()).isEmpty();
    }

    @Test
    void opensAnotherViewerOnceTheAttachGraceOfTheFirstHasExactlyPassed() {
        OsaScript osaScript = mock(OsaScript.class);
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(100_000L, 160_000L);
        var driver = new GhosttyTerminalDriver(
                OrchestratorProperties.defaults().withOpenTerminalWindow(true),
                osaScript, mock(Host.class), clock);

        driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));
        driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

        verify(osaScript, times(2)).run(anyString());
    }

    @Test
    void reportsNotRunningWhereGhosttyRefusesTheRevealScript() {
        OsaScript osaScript = mock(OsaScript.class);
        when(osaScript.answer(anyString())).thenThrow(new IllegalStateException("osascript failed"));
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), osaScript, mock(Host.class),
                Clock.systemUTC());

        assertThat(driver.reveal("agents")).isEqualTo(TerminalDriver.Revealed.NOT_RUNNING);
    }

    @Test
    void looksTheTabUpByItsTitleBeforeRevealingIt() {
        OsaScript osaScript = mock(OsaScript.class);
        when(osaScript.answer(anyString())).thenReturn("window");
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), osaScript, mock(Host.class),
                Clock.systemUTC());

        driver.reveal("agents");

        ArgumentCaptor<String> script = ArgumentCaptor.captor();
        verify(osaScript).answer(script.capture());
        assertThat(script.getValue()).contains("repeat with w in windows").contains("select tab hostTab");
    }

    @Test
    void bringsGhosttyToTheFront() {
        OsaScript osaScript = mock(OsaScript.class);
        var driver = new GhosttyTerminalDriver(OrchestratorProperties.defaults(), osaScript, mock(Host.class),
                Clock.systemUTC());

        driver.bringToFront();

        verify(osaScript).run("tell application \"Ghostty\" to activate");
    }
}
