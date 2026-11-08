package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandShellTest {

  @Test
  void aMacHoldsTheWakeLockWithCaffeinateForTheStandJvm() {
    assertThat(StandShell.awakeCommand("Mac OS X", 4242, name -> name.equals("caffeinate")))
        .contains(List.of("caffeinate", "-i", "-s", "-w", "4242"));
  }

  @Test
  void aLinuxWithSystemdInhibitBlocksSleepForTheStandJvm() {
    assertThat(
            StandShell.awakeCommand("Linux", 4242, name -> name.equals("systemd-inhibit"))
                .orElseThrow())
        .startsWith("systemd-inhibit")
        .contains("--pid=4242");
  }

  @Test
  void aHostWithoutTheToolHoldsNoWakeLock() {
    assertThat(StandShell.awakeCommand("Linux", 4242, name -> false)).isEmpty();
  }

  @Test
  void aStandRootIsDeletedAfterItsHupIgnoringPaneWriterIsStopped() throws Exception {
    Path base = Files.createTempDirectory(Path.of("/tmp"), "j");
    Path root = base.resolve("jagt-stand-1-1");
    Files.createDirectories(root.resolve("tmux/x"));
    Files.createDirectories(root.resolve("w"));
    Path socket = root.resolve("tmux/x/default");
    try {
      StandShell.run(
          root,
          List.of(
              "tmux",
              "-S",
              socket.toString(),
              "new-session",
              "-d",
              "sh",
              "-c",
              "trap '' HUP; while :; do : > " + root + "/w/f$RANDOM; done"));
      Thread.sleep(500);

      StandShell.killTmux(root);
      StandShell.deleteRoot(root);

      assertThat(root).doesNotExist();
    } finally {
      StandShell.run(base, List.of("tmux", "-S", socket.toString(), "kill-server"));
      StandShell.run(base, List.of("pkill", "-f", root.toString()));
      Files.deleteIfExists(base);
    }
  }
}
