package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogbackTargetTest {

  @Test
  void consoleWritesOneJsonObjectPerLineOnStdoutWithItsFieldsAndNoFile(@TempDir Path logs)
      throws Exception {
    Process probe =
        new ProcessBuilder(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp",
                System.getProperty("java.class.path"),
                LogbackProbe.class.getName(),
                logs.toString(),
                "jagt-console-logging")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    String stdout = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(probe.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(stdout.strip().lines()).hasSize(1);
    assertThat(stdout)
        .contains("\"message\":\"read failed\"")
        .contains("\"url\":\"http://example.test\"")
        .contains("\"attempt\":2")
        .startsWith("{")
        .endsWith("}\n");
    assertThat(logs).isEmptyDirectory();
  }

  @Test
  void fileKeepsTheHumanConsoleAndWritesTheEcsFileWhenLoggingIsUnset(@TempDir Path logs)
      throws Exception {
    Process probe =
        new ProcessBuilder(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp",
                System.getProperty("java.class.path"),
                LogbackProbe.class.getName(),
                logs.toString(),
                "")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    String stdout = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(probe.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(stdout).contains("read failed").doesNotStartWith("{");
    assertThat(logs.resolve("jagt-backend.log")).content().contains("\"message\":\"read failed\"");
  }
}
