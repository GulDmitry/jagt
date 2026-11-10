package dev.jagt.orchestrator.adapter.macos;

import static dev.jagt.orchestrator.adapter.macos.KittyFixtures.answeringEveryCommand;
import static dev.jagt.orchestrator.adapter.macos.KittyFixtures.driver;
import static dev.jagt.orchestrator.adapter.macos.KittyFixtures.plainDriver;
import static dev.jagt.orchestrator.adapter.macos.KittyFixtures.secondCommandOf;
import static dev.jagt.orchestrator.adapter.macos.KittyFixtures.viewerDriver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.adapter.KittyOptions;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.TerminalDriver;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KittyTerminalDriverTest {

  @Test
  void notesNothingAboutClosedViewerWhereItsKillToolIsOnPath() {
    var driver = plainDriver();

    assertThat(driver.notes()).isEmpty();
  }

  @Test
  void bindsCyrillicPasteAndCopySoPastingWorksOnTheRussianLayout() {
    var driver = plainDriver();

    assertThat(driver.platformOptions())
        .containsSequence("-o", "map=cmd+м paste_from_clipboard")
        .containsSequence("-o", "map=cmd+с copy_to_clipboard");
  }

  @Test
  void keepsTheLatinDefaultsInsteadOfRemappingPasteWhichWouldDropKittysAsciiFallback() {
    var driver = plainDriver();

    assertThat(driver.platformOptions())
        .doesNotContain("map=cmd+v paste_from_clipboard", "map=cmd+c copy_to_clipboard");
  }

  @Test
  void raisesTheAppWithAppleScriptBecauseKittyCannotPutItselfInFrontOnCocoa() {
    OsaScript osaScript = mock(OsaScript.class);

    new KittyTerminalDriver(
            mock(ProcessRunner.class),
            OrchestratorProperties.defaults(),
            osaScript,
            new KittyOptions("kitty", ""),
            Clock.systemUTC())
        .bringToFront();

    verify(osaScript).run("tell application \"kitty\" to activate");
  }

  @Test
  void opensNothingWhereTheHumanTurnedTheWindowOff() {
    ProcessRunner runner = mock(ProcessRunner.class);
    var driver =
        new KittyTerminalDriver(
            runner,
            OrchestratorProperties.defaults().withOpenTerminalWindow(false),
            mock(OsaScript.class),
            new KittyOptions("kitty", ""),
            Clock.systemUTC());

    driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

    verifyNoInteractions(runner);
  }

  @Test
  void startsTheViewerInstanceWhereNoneAnswers() {
    ProcessRunner runner = answeringEveryCommand(1);
    var driver = viewerDriver(runner);

    driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

    assertThat(secondCommandOf(runner))
        .startsWith("/k/kitty", "--detach")
        .containsSequence("--", "tmux", "attach", "-t", "agents");
  }

  @Test
  void firstViewerRunsTheAttachCommandItIsGiven() {
    ProcessRunner runner = answeringEveryCommand(1);
    var driver = viewerDriver(runner);

    driver.openViewer(
        List.of("tmux", "-L", "jagt-sock", "attach", "-t", "agents"),
        "agents",
        Path.of("/work/tree"));

    assertThat(secondCommandOf(runner))
        .containsSequence("--", "tmux", "-L", "jagt-sock", "attach", "-t", "agents");
  }

  @Test
  void extraTabRunsTheAttachCommandItIsGiven() {
    ProcessRunner runner = answeringEveryCommand(0);
    var driver = viewerDriver(runner);

    driver.openViewer(
        List.of("tmux", "-L", "jagt-sock", "attach", "-t", "agents"),
        "agents tab",
        Path.of("/work/tree"));

    assertThat(secondCommandOf(runner))
        .containsSequence("--", "tmux", "-L", "jagt-sock", "attach", "-t", "agents");
  }

  @Test
  void opensTabInTheViewerInstanceThatAnswers() {
    ProcessRunner runner = answeringEveryCommand(0);
    var driver = viewerDriver(runner);

    driver.openViewer(
        List.of("tmux", "attach", "-t", "agents"), "agents tab", Path.of("/work/tree"));

    assertThat(secondCommandOf(runner))
        .startsWith("/k/kitty", "@", "--to")
        .containsSequence(
            "launch", "--type=tab", "--tab-title", "agents tab", "--cwd", "/work/tree")
        .containsSequence("--", "tmux", "attach", "-t", "agents");
  }

  @Test
  void opensNoSecondViewerWhileTheFirstIsStillAttaching() {
    ProcessRunner runner = answeringEveryCommand(0);
    var driver = viewerDriver(runner);

    driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));
    driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));

    verify(runner, times(2)).run(isNull(), any(Duration.class), anyList());
  }

  @Test
  void opensViewerForAnotherSessionAtOnce() {
    ProcessRunner runner = answeringEveryCommand(0);
    var driver = viewerDriver(runner);

    driver.openViewer(List.of("tmux", "attach", "-t", "agents"), "agents", Path.of("/work/tree"));
    driver.openViewer(List.of("tmux", "attach", "-t", "others"), "others", Path.of("/work/tree"));

    verify(runner, times(4)).run(isNull(), any(Duration.class), anyList());
  }

  @Test
  void revealsNothingWhereNoViewerAnswers() {
    ProcessRunner runner = answeringEveryCommand(1);
    OsaScript osaScript = mock(OsaScript.class);
    var driver = driver(runner, osaScript);

    var revealed = driver.reveal("agents");

    assertThat(revealed).isEqualTo(TerminalDriver.Revealed.NOT_RUNNING);
    verify(osaScript, never()).run(any());
  }

  @Test
  void focusesTheViewerWindowAndRaisesTheAppWhereViewerAnswers() {
    ProcessRunner runner = answeringEveryCommand(0);
    OsaScript osaScript = mock(OsaScript.class);
    var driver = driver(runner, osaScript);

    var revealed = driver.reveal("agents");

    assertThat(revealed).isEqualTo(TerminalDriver.Revealed.WINDOW);
    assertThat(secondCommandOf(runner)).containsSequence("focus-window", "--match", "cmdline:tmux");
    verify(osaScript).run("tell application \"kitty\" to activate");
  }

  @Test
  void closesTheViewerByTheSocketOnlyItsInstanceHolds() {
    ProcessRunner runner = mock(ProcessRunner.class);
    var driver = driver(runner, mock(OsaScript.class));

    driver.closeViewerWindow("agents");

    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(isNull(), any(Duration.class), command.capture());
    assertThat(command.getValue())
        .startsWith("pkill", "-f")
        .last()
        .asString()
        .endsWith("jagt-kitty-agents");
  }
}
