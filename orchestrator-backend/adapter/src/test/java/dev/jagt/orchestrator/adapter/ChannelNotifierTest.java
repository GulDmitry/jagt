package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.NotificationsConfig.ChannelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelNotifierTest {

    private static ConfigService configWith(ChannelConfig channel) {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withNotifications(new NotificationsConfig(null, channel)));
        return config;
    }

    @Test
    void isTheNotifierTheConfigNamesChannel() {
        assertThat(new ChannelNotifier(mock(ConfigService.class), new ProcessRunner()).id()).isEqualTo("channel");
    }

    @Test
    void takesAListedEvent() {
        ChannelNotifier channel = new ChannelNotifier(
                configWith(new ChannelConfig(List.of("cat"), List.of("deployed"))), new ProcessRunner());

        assertThat(channel.takes(Notification.deployed("ABC-42", "landed"))).isTrue();
    }

    @Test
    void takesNothingForAnEventTheListLeavesOut() {
        ChannelNotifier channel = new ChannelNotifier(
                configWith(new ChannelConfig(List.of("cat"), List.of("deployed"))), new ProcessRunner());

        assertThat(channel.takes(Notification.requestOpened("ABC-42", "!1"))).isFalse();
    }

    @Test
    void takesNothingWhenTheCommandIsBlank() {
        ChannelNotifier channel = new ChannelNotifier(
                configWith(new ChannelConfig(List.of(), null)), new ProcessRunner());

        assertThat(channel.takes(Notification.deployed("ABC-42", "landed"))).isFalse();
    }

    @Test
    void takesARequestOpeningAndADeployByDefault() {
        ChannelNotifier channel = new ChannelNotifier(
                configWith(new ChannelConfig(List.of("cat"), null)), new ProcessRunner());

        assertThat(channel.takes(Notification.requestOpened("ABC-42", "!1"))).isTrue();
        assertThat(channel.takes(Notification.usage("window", "full"))).isFalse();
    }

    @Test
    void deliversTheNotificationAsOneJsonObjectOnTheCommandsInput(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out.json");
        ChannelNotifier channel = new ChannelNotifier(configWith(new ChannelConfig(
                List.of("sh", "-c", "cat > " + out), null)), new ProcessRunner());

        channel.deliver(Notification.deployed("ABC-42", "landed"));

        assertThat(Files.readString(out))
                .isEqualTo("{\"event\":\"deployed\",\"task\":\"ABC-42\",\"title\":\"deployed\",\"body\":\"landed\"}");
    }

    @Test
    void doesNotThrowWhenTheCommandFails() {
        ChannelNotifier channel = new ChannelNotifier(configWith(new ChannelConfig(
                List.of("sh", "-c", "exit 3"), null)), new ProcessRunner());

        assertThatCode(() -> channel.deliver(Notification.deployed("ABC-42", "landed"))).doesNotThrowAnyException();
    }

    @Test
    void doesNotThrowWhenTheCommandCannotStart() {
        ChannelNotifier channel = new ChannelNotifier(configWith(new ChannelConfig(
                List.of("no-such-binary-abc"), null)), new ProcessRunner());

        assertThatCode(() -> channel.deliver(Notification.deployed("ABC-42", "landed"))).doesNotThrowAnyException();
    }
}
