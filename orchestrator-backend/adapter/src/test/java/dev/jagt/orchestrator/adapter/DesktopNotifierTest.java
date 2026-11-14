package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.port.channel.ChannelAddress;
import dev.jagt.orchestrator.port.channel.ChannelMessage;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.service.log.BoardUrl;
import java.util.List;
import org.junit.jupiter.api.Test;

class DesktopNotifierTest {

  private static final ChannelAddress DESKTOP =
      new ChannelAddress("desktop", "desktop", "", List.of());

  @Test
  void clicksThroughToTheTasksCard() {
    UserNotifier os = mock(UserNotifier.class);

    new DesktopNotifier(os, new BoardUrl("8290"))
        .deliver(
            DESKTOP,
            new ChannelMessage(
                "stopped", "ABC-42", "p3", "jagt · p3 · ABC-42 · stopped", "silent for 6 min"));

    verify(os)
        .notify(
            "jagt · p3 · ABC-42 · stopped",
            "silent for 6 min",
            "http://localhost:8290/?task=ABC-42");
  }

  @Test
  void linksNothingWhenTheMessageIsAboutNoTask() {
    UserNotifier os = mock(UserNotifier.class);

    new DesktopNotifier(os, new BoardUrl("8290"))
        .deliver(
            DESKTOP,
            new ChannelMessage(
                "install", null, null, "jagt · restart needed", "the jar was rebuilt"));

    verify(os).notify("jagt · restart needed", "the jar was rebuilt", null);
  }

  @Test
  void isTheDriverOfKindDesktop() {
    assertThat(new DesktopNotifier(mock(UserNotifier.class), new BoardUrl("8290")).kind())
        .isEqualTo("desktop");
  }
}
