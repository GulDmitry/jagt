package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.adapter.DesktopNotifier;
import dev.jagt.orchestrator.port.UserNotifier;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DesktopNotifierTest {

    @Test
    void namesTheTaskInTheBannerTitleWhenTheNotificationIsAboutOne() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os).deliver(Notification.watchdog("ABC-42", "agent unresponsive", "silent for 6 min"));

        verify(os).notify("jagt · ABC-42 · agent unresponsive", "silent for 6 min");
    }

    @Test
    void namesNoTaskWhenTheNotificationIsAboutTheInstallItself() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os).deliver(Notification.install("restart needed", "the running jar was rebuilt"));

        verify(os).notify("jagt · restart needed", "the running jar was rebuilt");
    }
}
