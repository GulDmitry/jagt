package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesktopNotifierTest {

    @Test
    void namesTheTaskInTheBannerTitleAndClicksThroughToItsCard() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os, mock(ConfigService.class), "8290")
                .deliver(Notification.watchdog("ABC-42", "agent unresponsive", "silent for 6 min"));

        verify(os).notify("jagt · ABC-42 · agent unresponsive", "silent for 6 min",
                "http://localhost:8290/?task=ABC-42");
    }

    @Test
    void namesNoTaskWhenTheNotificationIsAboutTheInstallItself() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os, mock(ConfigService.class), "8290")
                .deliver(Notification.install("restart needed", "the running jar was rebuilt"));

        verify(os).notify("jagt · restart needed", "the running jar was rebuilt", null);
    }

    @Test
    void namesTheAliasBeforeTheTaskIdInTheBannerTitle() {
        UserNotifier os = mock(UserNotifier.class);

        new DesktopNotifier(os, mock(ConfigService.class), "8290")
                .deliver(Notification.watchdog("ABC-42", "agent stopped", "silent for 6 min").withAlias("p3"));

        verify(os).notify("jagt · p3 · ABC-42 · agent stopped", "silent for 6 min",
                "http://localhost:8290/?task=ABC-42");
    }

    @Test
    void isTheNotifierTheConfigNamesDesktop() {
        assertThat(new DesktopNotifier(mock(UserNotifier.class), mock(ConfigService.class), "8290").id()).isEqualTo("desktop");
    }

    @Test
    void takesWhatTheDefaultConfigListsAndNotADeploy() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults());
        DesktopNotifier desktop = new DesktopNotifier(mock(UserNotifier.class), config, "8290");

        assertThat(desktop.takes(Notification.requestOpened("ABC-42", "!1"))).isTrue();
        assertThat(desktop.takes(Notification.watchdog("ABC-42", "agent stopped", "silent"))).isTrue();
        assertThat(desktop.takes(Notification.deployed("ABC-42", "deployed"))).isFalse();
    }

    @Test
    void takesADeployOnlyWhenTheConfigListsDeployed() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withNotifications(new NotificationsConfig(List.of("deployed"))));
        DesktopNotifier desktop = new DesktopNotifier(mock(UserNotifier.class), config, "8290");

        assertThat(desktop.takes(Notification.deployed("ABC-42", "deployed"))).isTrue();
        assertThat(desktop.takes(Notification.requestOpened("ABC-42", "!1"))).isFalse();
        assertThat(desktop.takes(Notification.usage("window", "x"))).isFalse();
    }

    @Test
    void takesNothingWhenTheConfigListsOnlyAnUnknownEvent() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withNotifications(new NotificationsConfig(List.of("no-such-event"))));

        assertThat(new DesktopNotifier(mock(UserNotifier.class), config, "8290")
                .takes(Notification.install("restart needed", "x"))).isFalse();
    }
}
