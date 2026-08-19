package dev.jagt.orchestrator.notify;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationsTest {

    @Test
    void handsANotificationOnlyToTheChannelsThatWantIt() {
        Notifier uninterested = mock(Notifier.class);
        Notifier interested = mock(Notifier.class);
        when(uninterested.takes(any())).thenReturn(false);
        when(interested.takes(any())).thenReturn(true);
        Notification orphans = Notification.housekeeping("2 orphaned worktree(s)", "see the log");

        new Notifications(List.of(uninterested, interested), Runnable::run).send(orphans);

        verify(uninterested, never()).deliver(any());
        verify(interested).deliver(orphans);
    }

    @Test
    void stillReachesEveryOtherChannelWhenOneOfThemFails() {
        Notifier broken = mock(Notifier.class);
        Notifier working = mock(Notifier.class);
        when(broken.takes(any())).thenReturn(true);
        when(working.takes(any())).thenReturn(true);
        doThrow(new RuntimeException("no notification daemon")).when(broken).deliver(any());
        Notification alert = Notification.watchdog("ABC-42", "agent unresponsive", "silent for 6 min");

        new Notifications(List.of(broken, working), Runnable::run).send(alert);

        verify(working).deliver(alert);
    }
}
