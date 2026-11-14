package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LsofWorktreeProcessesTest {

  @Test
  void reapSparesTheTmuxViewerButTakesAgentDaemonsUnderTheWorktree() {
    String lsof =
        """
        p100
        cjava
        fcwd
        n/Users/x/www/wt
        p200
        cnode
        fcwd
        n/Users/x/www/wt/app
        p300
        ctmux
        fcwd
        n/Users/x/www/wt
        p400
        cjava
        fcwd
        n/Users/x/www/other\
        """;

    assertThat(LsofWorktreeProcesses.reapable(lsof, "/Users/x/www/wt"))
        .extracting(LsofWorktreeProcesses.Reapable::pid)
        .containsExactly("100", "200");
  }

  @Test
  void reapKillsTheProcessWhoseWorkingDirectoryIsTheWorktree(@TempDir Path worktree)
      throws Exception {
    Path real = worktree.toRealPath();
    Process held = new ProcessBuilder("sleep", "60").directory(real.toFile()).start();
    Processes runner = mock(Processes.class);
    when(runner.run(isNull(), any(Duration.class), any()))
        .thenReturn(new Processes.Result(0, "p" + held.pid() + "\ncsleep\nfcwd\nn" + real, ""));

    new LsofWorktreeProcesses(runner).reap(real);

    assertThat(held.waitFor(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void logsOneLineForWorktreeHowEverManyProcessesItHeld() {
    try (FakeLogCapture log = new FakeLogCapture(LsofWorktreeProcesses.class)) {
      Processes runner = mock(Processes.class);
      when(runner.run(isNull(), any(Duration.class), any()))
          .thenReturn(
              new Processes.Result(
                  0,
                  """
                  p2147483646
                  cclaude
                  fcwd
                  n/nonexistent/wt
                  p2147483647
                  cnode
                  fcwd
                  n/nonexistent/wt/app\
                  """,
                  ""));

      new LsofWorktreeProcesses(runner).reap(Path.of("/nonexistent/wt"));

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("worktree processes reaped");
    }
  }
}
