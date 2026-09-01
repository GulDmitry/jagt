package dev.jagt.orchestrator.linux;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import dev.jagt.orchestrator.port.Processes;
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
            Thread.sleep(500);                       // let the monitor attach before anything is sent
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
            Thread.sleep(500);
            new LibNotifyNotifier(new ProcessRunner(), "notify-send")
                    .notify("--urgency=critical looking title", "body", null);

            assertThat(awaitCapture(capture, "looking title"))
                    .contains("\"--urgency=critical looking title\"");
        } finally {
            monitor.destroy();
            Files.deleteIfExists(capture);
        }
    }

    @Test
    void staysSilentWhenTheBinaryIsMissingBecauseAWatchdogMustNotDieOfIt() {
        new LibNotifyNotifier(new ProcessRunner(), "notify-send-that-does-not-exist")
                .notify("jagt", "nothing should happen", null);
    }

    /** dbus-monitor writes asynchronously; poll until the message shows up rather than sleeping blind. */
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
