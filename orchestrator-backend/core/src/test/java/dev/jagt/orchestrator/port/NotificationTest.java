package dev.jagt.orchestrator.port;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Point;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  void routesAnAgentNotificationOnItsTopicWithTheTaskItIsAbout() {
    Notification told =
        Notification.fromAgent(Point.YOUR_MOVE.id(), "ABC-1", "question", "which branch?");

    assertThat(told)
        .isEqualTo(
            new Notification(
                Notification.Topic.AGENT,
                Point.YOUR_MOVE.id(),
                "ABC-1",
                null,
                "question",
                "which branch?"));
  }

  @Test
  void routesTheOtherNotificationsOnTheirOwnTopicsAndNamesTaskOnlyForTheWatchdog() {
    assertThat(Notification.watchdog(Point.STOPPED.id(), "ABC-1", "quiet", "no report").topic())
        .isEqualTo(Notification.Topic.WATCHDOG);
    assertThat(Notification.watchdog(Point.STOPPED.id(), "ABC-1", "quiet", "no report").taskId())
        .isEqualTo("ABC-1");
    assertThat(Notification.housekeeping(Point.HOUSEKEEPING.id(), "tidy", "x").topic())
        .isEqualTo(Notification.Topic.HOUSEKEEPING);
    assertThat(Notification.install(Point.INSTALL.id(), "act", "x").topic())
        .isEqualTo(Notification.Topic.INSTALL);
    assertThat(Notification.usage(Point.USAGE.id(), "window", "x").topic())
        .isEqualTo(Notification.Topic.USAGE);
    assertThat(Notification.install(Point.INSTALL.id(), "act", "x").taskId()).isNull();
  }

  @Test
  void raisesAnOpenedRequestAndLandedDeployAsTheirOwnPointsOfTask() {
    assertThat(Notification.requestOpened(Point.REQUEST_OPENED.id(), "ABC-1", "!7"))
        .isEqualTo(
            new Notification(
                Notification.Topic.AGENT,
                Point.REQUEST_OPENED.id(),
                "ABC-1",
                null,
                "request opened",
                "!7"));
    assertThat(Notification.deployed(Point.DEPLOYED.id(), "ABC-1", "deployed"))
        .isEqualTo(
            new Notification(
                Notification.Topic.AGENT,
                Point.DEPLOYED.id(),
                "ABC-1",
                null,
                "deployed",
                "deployed"));
  }
}
