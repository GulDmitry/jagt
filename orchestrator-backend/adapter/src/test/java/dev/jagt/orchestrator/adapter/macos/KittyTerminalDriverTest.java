package dev.jagt.orchestrator.adapter.macos;

import static dev.jagt.orchestrator.adapter.macos.TestKittyTerminalDriverBuilder.kittyDriver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class KittyTerminalDriverTest {

  private final ProcessRunner runner = mock(ProcessRunner.class);
  private final OsaScript osaScript = mock(OsaScript.class);
  private final ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
  private final Clock clock = Clock.systemUTC();
  private final KittyTerminalDriver driver = kittyDriver().build(runner, osaScript, clock);
  private final KittyTerminalDriver viewer =
      kittyDriver()
          .withProperties(OrchestratorProperties.defaults().withOpenTerminalWindow(true))
          .build(runner, osaScript, clock);

  @Test
  void notesNothingAboutClosedViewerWhereItsKillToolIsOnPath() {

    assertThat(driver.notes()).isEmpty();
  }

  @Test
  void bindsCyrillicPasteAndCopySoPastingWorksOnTheRussianLayout() {

    assertThat(driver.platformOptions())
        .containsSequence("-o", "map=cmd+м paste_from_clipboard")
        .containsSequence("-o", "map=cmd+с copy_to_clipboard");
  }

  @Test
  void keepsTheLatinDefaultsInsteadOfRemappingPasteWhichWouldDropKittysAsciiFallback() {

    assertThat(driver.platformOptions())
        .doesNotContain("map=cmd+v paste_from_clipboard", "map=cmd+c copy_to_clipboard");
  }

  @Test
  void raisesTheAppWithAppleScriptBecauseKittyCannotPutItselfInFrontOnCocoa() {
    driver.bringToFront();

    verify(osaScript).run("tell application \"kitty\" to activate");
  }

  @Test
  void opensNothingWhereTheHumanTurnedTheWindowOff() {
    var driver =
        kittyDriver()
            .withProperties(OrchestratorProperties.defaults().withOpenTerminalWindow(false))
            .build(runner, osaScript, clock);

    driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

    verifyNoInteractions(runner);
  }

  @Test
  void startsTheViewerInstanceWhereNoneAnswers() {
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", ""));

    viewer.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

    verify(runner, times(2)).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues().get(1))
        .startsWith("/k/kitty", "--detach")
        .containsSequence("--", "tmux", "attach", "-t", "agents");
  }

  @Test
  void firstViewerRunsTheAttachCommandItIsGiven() {
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", ""));

    viewer.openViewer(
        List.of("tmux", "-L", "jagt-sock", "attach", "-t", "agents"),
        "agents",
        Path.of("/work/tree"));

    verify(runner, times(2)).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues().get(1))
        .containsSequence("--", "tmux", "-L", "jagt-sock", "attach", "-t", "agents");
  }

  @Test
  void extraTabRunsTheAttachCommandItIsGiven() {
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    viewer.openViewer(
        List.of("tmux", "-L", "jagt-sock", "attach", "-t", "agents"),
        "agents tab",
        Path.of("/work/tree"));

    verify(runner, times(2)).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues().get(1))
        .containsSequence("--", "tmux", "-L", "jagt-sock", "attach", "-t", "agents");
  }

  @Test
  void opensTabInTheViewerInstanceThatAnswers() {
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    viewer.openViewer(
        List.of("tmux", "attach", "-t", "agents"), "agents tab", Path.of("/work/tree"));

    verify(runner, times(2)).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues().get(1))
        .startsWith("/k/kitty", "@", "--to")
        .containsSequence(
            "launch", "--type=tab", "--tab-title", "agents tab", "--cwd", "/work/tree")
        .containsSequence("--", "tmux", "attach", "-t", "agents");
  }

  @ParameterizedTest(name = "a second viewer for {0} brings the commands to {1}")
  @CsvSource({"agents,2", "others,4"})
  void opensNoSecondViewerForTheSessionStillAttachingButOneForAnotherAtOnce(
      String second, int total) {
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    viewer.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));
    viewer.openViewer(List.of("tmux", "attach", "-t", second), second, Path.of("/work/tree"));

    verify(runner, times(total)).run(isNull(), any(Duration.class), anyList());
  }

  @Test
  void revealsNothingWhereNoViewerAnswers() {
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", ""));

    var revealed = driver.reveal("agents");

    assertThat(revealed).isEqualTo(TerminalDriver.Revealed.NOT_RUNNING);
    verify(osaScript, never()).run(any());
  }

  @Test
  void focusesTheViewerWindowAndRaisesTheAppWhereViewerAnswers() {
    when(runner.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));

    var revealed = driver.reveal("agents");

    assertThat(revealed).isEqualTo(TerminalDriver.Revealed.WINDOW);
    verify(runner, times(2)).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues().get(1))
        .containsSequence("focus-window", "--match", "cmdline:tmux");
    verify(osaScript).run("tell application \"kitty\" to activate");
  }

  @Test
  void closesTheViewerByTheSocketOnlyItsInstanceHolds() {

    driver.closeViewerWindow("agents");

    verify(runner).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getValue())
        .startsWith("pkill", "-f")
        .last()
        .asString()
        .endsWith("jagt-kitty-agents");
  }
}
