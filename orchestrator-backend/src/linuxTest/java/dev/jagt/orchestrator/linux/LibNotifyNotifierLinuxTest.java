package dev.jagt.orchestrator.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LibNotifyNotifierLinuxTest {

  private Path capture;
  private Process monitor;

  @BeforeEach
  void watchTheSessionBus() throws Exception {
    capture = Files.createTempFile("jagt-bus", ".txt");
    monitor =
        new ProcessBuilder(
                "dbus-monitor",
                "--session",
                "interface=org.freedesktop.Notifications,member=Notify")
            .redirectOutput(capture.toFile())
            .redirectErrorStream(true)
            .start();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(250))
        .until(() -> Files.readString(capture), seen -> seen.contains("NameAcquired"));
  }

  @AfterEach
  void stopWatching() throws Exception {
    monitor.destroy();
    Files.deleteIfExists(capture);
  }

  @Test
  void deliversTheNotificationToTheSessionBusWithJagtAsTheApplication() {
    new LibNotifyNotifier(new ProcessRunner(), "notify-send")
        .notify("jagt · ABC-1", "your move: read the diff", null);

    assertThat(
            await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .until(
                    () -> Files.readString(capture),
                    seen -> seen.contains("org.freedesktop.Notifications")))
        .as("the Notify call as the desktop received it")
        .contains("member=Notify")
        .contains("\"jagt\"")
        .contains("\"jagt · ABC-1\"")
        .contains("\"your move: read the diff\"")
        .contains("urgency");
  }

  @Test
  void sendsTitleThatLooksLikeAnOptionInsteadOfSwallowingIt() {
    new LibNotifyNotifier(new ProcessRunner(), "notify-send")
        .notify("--urgency=critical looking title", "body", null);

    assertThat(
            await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> Files.readString(capture), seen -> seen.contains("looking title")))
        .contains("\"--urgency=critical looking title\"");
  }
}
