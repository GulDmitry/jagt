package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.UserNotifier;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DesktopNotifierTest {

    @Test
    void namesTheTaskInTheBannerTitleAndClicksThroughToItsCard() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os, "web", "8290")
                .deliver(Notification.watchdog("ABC-42", "agent unresponsive", "silent for 6 min"));

        verify(os).notify("jagt · ABC-42 · agent unresponsive", "silent for 6 min",
                "http://localhost:8290/?task=ABC-42");
    }

    @Test
    void namesNoTaskWhenTheNotificationIsAboutTheInstallItself() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os, "web", "8290")
                .deliver(Notification.install("restart needed", "the running jar was rebuilt"));

        verify(os).notify("jagt · restart needed", "the running jar was rebuilt", null);
    }

    /** A click that opens a dead page is worse than one that does nothing. */
    @Test
    void offersNoClickThroughWhenNoBoardIsBeingServed() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os, "tui", "8290")
                .deliver(Notification.watchdog("ABC-42", "agent unresponsive", "silent for 6 min"));

        verify(os).notify("jagt · ABC-42 · agent unresponsive", "silent for 6 min", null);
    }

    @Test
    void clicksThroughWhenBothSurfacesAreServed() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os, "both", "9000")
                .deliver(Notification.fromAgent("ABC-1", "needs input", "answer the question"));

        verify(os).notify("jagt · ABC-1 · needs input", "answer the question",
                "http://localhost:9000/?task=ABC-1");
    }
}
