package dev.jagt.orchestrator.adapter;

import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

final class ProcessFixtures {

  private ProcessFixtures() {}

  static long awaitedPid(Path pidFile) throws Exception {
    await()
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(5))
        .until(() -> Files.readString(pidFile).endsWith("\n"));
    return Long.parseLong(Files.readString(pidFile).strip());
  }
}
