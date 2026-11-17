package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandCleanupMainTest {

  @TempDir Path directory;

  @Test
  void mainRemovesTheRootsOfDeadRuns() throws Exception {
    Path base = Files.createDirectories(directory.resolve("tmp")).toRealPath();
    Files.createDirectory(base.resolve("jagt-stand-999999999-1"));

    StandCleanup.main(new String[] {base.toString(), directory.resolve("failed").toString()});

    assertThat(base.resolve("jagt-stand-999999999-1")).doesNotExist();
  }
}
