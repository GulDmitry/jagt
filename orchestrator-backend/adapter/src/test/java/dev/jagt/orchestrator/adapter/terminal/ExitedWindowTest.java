package dev.jagt.orchestrator.adapter.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExitedWindowTest {

  @Test
  void keepsTheLastThreeLinesPrintedBeforeTheExitMarker() {
    String shown = "one\n\ntwo\nthree\nfour\n[jagt] agent exited — window closes in 15s\n";

    assertThat(ExitedWindow.printedBeforeExit(shown)).contains("two\nthree\nfour");
  }

  @Test
  void readsNothingWhereTheScreenHoldsNoExitMarker() {
    assertThat(ExitedWindow.printedBeforeExit("one\ntwo\n")).isEmpty();
  }

  @Test
  void knowsTheAgentExitedOnceTheMarkerIsOnScreen() {
    assertThat(ExitedWindow.hasExited("out\n[jagt] agent exited — window closes in 15s\n"))
        .isTrue();
  }

  @Test
  void knowsTheAgentHasNotExitedWhereNoMarkerIsOnScreen() {
    assertThat(ExitedWindow.hasExited("out\n")).isFalse();
  }

  @Test
  void leavesNoShellBehindTheCommandItRuns() {
    assertThat(ExitedWindow.shellLine("agent --go"))
        .startsWith("agent --go; ")
        .endsWith("exec sleep 15");
  }
}
