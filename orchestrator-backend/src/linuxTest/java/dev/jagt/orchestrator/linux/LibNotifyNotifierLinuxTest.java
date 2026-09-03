package dev.jagt.orchestrator.linux;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LibNotifyNotifierLinuxTest {

    @Test
    void deliversTheNotificationToTheSessionBusWithJagtAsTheApplication() throws Exception {
        Path capture = Files.createTempFile("jagt-bus", ".txt");
        Process monitor = new ProcessBuilder("dbus-monitor", "--session",
                "interface=org.freedesktop.Notifications,member=Notify")
                .redirectOutput(capture.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            awaitCapture(capture, "NameAcquired");
            new LibNotifyNotifier(new ProcessRunner(), "notify-send")
                    .notify("jagt · ABC-1", "your move: read the diff", null);

            assertThat(awaitCapture(capture, "org.freedesktop.Notifications"))
                    .as("the Notify call as the desktop received it")
                    .contains("member=Notify")
                    .contains("\"jagt\"")
                    .contains("\"jagt · ABC-1\"")
                    .contains("\"your move: read the diff\"")
                    .contains("urgency");
        } finally {
            monitor.destroy();
            Files.deleteIfExists(capture);
        }
    }

    @Test
    void sendsATitleThatLooksLikeAnOptionInsteadOfSwallowingIt() throws Exception {
        Path capture = Files.createTempFile("jagt-bus", ".txt");
        Process monitor = new ProcessBuilder("dbus-monitor", "--session",
                "interface=org.freedesktop.Notifications,member=Notify")
                .redirectOutput(capture.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            awaitCapture(capture, "NameAcquired");
            new LibNotifyNotifier(new ProcessRunner(), "notify-send")
                    .notify("--urgency=critical looking title", "body", null);

            assertThat(awaitCapture(capture, "looking title"))
                    .contains("\"--urgency=critical looking title\"");
        } finally {
            monitor.destroy();
            Files.deleteIfExists(capture);
        }
    }

    /**
     * dbus-monitor writes asynchronously, so every wait on it is a poll rather than a blind sleep — the bus
     * hands it a NameAcquired for its own connection, which is the only proof it is attached and listening.
     */
    private static String awaitCapture(Path capture, String expected) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            String seen = Files.exists(capture) ? Files.readString(capture) : "";
            if (seen.contains(expected)) {
                return seen;
            }
            Thread.sleep(250);
        }
        return Files.exists(capture) ? Files.readString(capture) : "";
    }
}
