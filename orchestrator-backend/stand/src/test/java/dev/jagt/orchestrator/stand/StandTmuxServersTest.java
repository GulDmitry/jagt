package dev.jagt.orchestrator.stand;

import static dev.jagt.orchestrator.stand.TestTmuxServerBuilder.tmuxServer;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandTmuxServersTest {

  @TempDir Path directory;

  @Test
  void tmuxServerOfDeletedStandRootIsStopped() throws Exception {
    Path base = Files.createDirectory(directory.resolve("b")).toRealPath();
    Path root = Files.createDirectory(base.resolve("jagt-stand-1-1"));
    TestTmuxServerBuilder.Server server = tmuxServer(directory, root).build();
    try {
      Files.delete(root);

      StandTmuxServers.stopOrphaned(base);

      assertThat(server.running()).isFalse();
    } finally {
      server.stop();
    }
  }

  @Test
  void tmuxServerOfAnotherDeletedDirectoryKeepsRunning() throws Exception {
    Path base = Files.createDirectory(directory.resolve("b")).toRealPath();
    Path work = Files.createDirectory(base.resolve("work-1-1"));
    TestTmuxServerBuilder.Server server = tmuxServer(directory, work).build();
    try {
      Files.delete(work);

      StandTmuxServers.stopOrphaned(base);

      assertThat(server.running()).isTrue();
    } finally {
      server.stop();
    }
  }

  @Test
  void tmuxServerOfAnExistingStandRootKeepsRunning() throws Exception {
    Path base = Files.createDirectory(directory.resolve("b")).toRealPath();
    Path root = Files.createDirectory(base.resolve("jagt-stand-1-1"));
    TestTmuxServerBuilder.Server server = tmuxServer(directory, root).build();
    try {
      StandTmuxServers.stopOrphaned(base);

      assertThat(server.running()).isTrue();
    } finally {
      server.stop();
    }
  }
}
