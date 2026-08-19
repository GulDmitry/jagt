package dev.jagt.orchestrator.linux;

import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import dev.jagt.orchestrator.service.ProcessRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LibNotifyNotifier} against the REAL {@code notify-send}, a real session bus and a real notification
 * daemon — the only way to know that a Linux notification actually leaves the process, since the driver's unit
 * test can only assert the argv it builds.
 *
 * <p>Assertion method: a {@code dbus-monitor} started by the test records the {@code Notify} method call on
 * the session bus, so what is checked is the wire message the desktop receives, not jagt's own log. The daemon
 * (dunst) has to be there for {@code notify-send} to exit 0 at all; the container harness starts both it and
 * the Xvfb display before this task runs (see {@code scripts/linux-suite.sh}).
 */
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
                    .notify("jagt · ABC-1", "your move: read the diff");

            assertThat(awaitCapture(capture, "org.freedesktop.Notifications"))
                    .as("the Notify call as the desktop received it")
                    .contains("member=Notify")
                    // --app-name jagt: the banners must be attributable (and mutable) as jagt's own.
                    .contains("\"jagt\"")
                    .contains("\"jagt · ABC-1\"")
                    .contains("\"your move: read the diff\"")
                    // NORMAL urgency, deliberately: "your move" should persist, not override do-not-disturb.
                    .contains("urgency");
        } finally {
            monitor.destroy();
            Files.deleteIfExists(capture);
        }
    }

    /**
     * A title beginning with a dash comes from ticket text, and without the {@code --} guard notify-send would
     * parse it as an option — the notification would silently never appear.
     */
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
                    .notify("--urgency=critical looking title", "body");

            assertThat(awaitCapture(capture, "looking title"))
                    .contains("\"--urgency=critical looking title\"");
        } finally {
            monitor.destroy();
            Files.deleteIfExists(capture);
        }
    }

    /** The notifier is best-effort by contract, so a missing binary must not throw at the calling flow. */
    @Test
    void staysSilentWhenTheBinaryIsMissingBecauseAWatchdogMustNotDieOfIt() {
        new LibNotifyNotifier(new ProcessRunner(), "notify-send-that-does-not-exist")
                .notify("jagt", "nothing should happen");
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
