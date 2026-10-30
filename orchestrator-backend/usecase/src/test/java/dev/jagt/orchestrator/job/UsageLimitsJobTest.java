package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.service.UsageLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UsageLimitsJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void tellsTheHumanWhenAWindowCrossesAThreshold(@TempDir Path root) {
        SubscriptionUsage usage = () -> new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(
                List.of(new SubscriptionUsage.Window("week", 81, Optional.empty())), Optional.empty()));
        Notifications notifications = mock(Notifications.class);

        new UsageLimitsJob(new UsageLimits(Optional.of(usage)), notifications, UsageLimitsProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())), CLOCK)
                .run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.USAGE
                && sent.title().equals("week window at 80%")));
    }

    @Test
    void tellsNothingWhenTheReadFailed(@TempDir Path root) {
        SubscriptionUsage usage = () -> new SubscriptionUsage.Unreadable("http 500");
        Notifications notifications = mock(Notifications.class);

        new UsageLimitsJob(new UsageLimits(Optional.of(usage)), notifications, UsageLimitsProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())), CLOCK)
                .run();

        verify(notifications, never()).send(any());
    }
}
