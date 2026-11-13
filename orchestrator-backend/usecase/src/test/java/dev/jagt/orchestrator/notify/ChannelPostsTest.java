package dev.jagt.orchestrator.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.ChannelAddress;
import dev.jagt.orchestrator.port.ChannelDriver;
import dev.jagt.orchestrator.port.ChannelMessage;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelPostsTest {

  private static final ChannelAddress TEAM = new ChannelAddress("team", "teams", "HOOK", List.of());
  private static final ChannelMessage DEPLOYED =
      new ChannelMessage("deployed", "ABC-42", "p3", "", "p3 is live");

  private final ChannelDriver driver = mock(ChannelDriver.class);
  private final Reach reach = mock(Reach.class);
  private final ScheduledExecutorService retries = mock(ScheduledExecutorService.class);
  private final ChannelPosts posts =
      new ChannelPosts(List.of(driver), reach, Runnable::run, retries);

  @BeforeEach
  void driverOfKindTeams() {
    when(driver.kind()).thenReturn("teams");
  }

  @Test
  void postsTheMessageWhereTheAddressPointsAndClearsTheMark() {
    posts.post(TEAM, DEPLOYED);

    verify(driver).deliver(TEAM, DEPLOYED);
    verify(reach).reached("notification", "team/deployed");
  }

  @Test
  void marksTheBoardNamingChannelPointAndCauseWhenThePostDoesNotLand() {
    doThrow(new IllegalStateException("HTTP 500")).when(driver).deliver(any(), any());

    posts.post(TEAM, DEPLOYED);

    verify(reach)
        .marked(
            new ReachLost(
                "notification", "team/deployed", List.of(), "team cannot tell deployed: HTTP 500"));
    verify(reach, never()).reached(any(), any());
  }

  @Test
  void triesAgainAfterTenSecondsThenTwentyThenEveryThirty() {
    ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
    doThrow(new IllegalStateException("HTTP 500")).when(driver).deliver(any(), any());

    posts.post(TEAM, DEPLOYED);
    verify(retries).schedule(retry.capture(), eq(10L), eq(TimeUnit.SECONDS));
    retry.getValue().run();
    verify(retries).schedule(retry.capture(), eq(20L), eq(TimeUnit.SECONDS));
    retry.getValue().run();
    verify(retries).schedule(retry.capture(), eq(30L), eq(TimeUnit.SECONDS));
    retry.getValue().run();

    verify(retries, times(2)).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
  }

  @Test
  void keepsOnePendingPostPerChannelPointAndTaskWhileItFails() {
    doThrow(new IllegalStateException("HTTP 500")).when(driver).deliver(any(), any());

    posts.post(TEAM, DEPLOYED);
    posts.post(TEAM, DEPLOYED);
    posts.post(TEAM, DEPLOYED);

    verify(driver, times(1)).deliver(any(), any());
    verify(retries, times(1)).schedule(any(Runnable.class), anyLong(), any());
  }

  @Test
  void postsPendingPointsOfTwoTasksApart() {
    doThrow(new IllegalStateException("HTTP 500")).when(driver).deliver(any(), any());

    posts.post(TEAM, DEPLOYED);
    posts.post(TEAM, new ChannelMessage("deployed", "ABC-43", "p4", "", "p4 is live"));

    verify(retries, times(2)).schedule(any(Runnable.class), anyLong(), any());
  }

  @Test
  void stopsTryingAndClearsTheMarkOnTheFirstRetryThatLands() {
    ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
    doThrow(new IllegalStateException("HTTP 500")).doNothing().when(driver).deliver(any(), any());

    posts.post(TEAM, DEPLOYED);
    verify(retries).schedule(retry.capture(), eq(10L), eq(TimeUnit.SECONDS));
    retry.getValue().run();

    verify(reach).reached("notification", "team/deployed");
    verify(retries, times(1)).schedule(any(Runnable.class), anyLong(), any());
  }

  @Test
  void postsThroughTheDriverOfTheAddressesKindOnly() {
    ChannelDriver desktop = mock(ChannelDriver.class);
    when(desktop.kind()).thenReturn("desktop");

    new ChannelPosts(List.of(desktop, driver), reach, Runnable::run, retries).post(TEAM, DEPLOYED);

    verify(desktop, never()).deliver(any(), any());
  }

  @Test
  void failsThePostWhenNoDriverHasTheChannelsKind() {
    new ChannelPosts(List.of(), reach, Runnable::run, retries).post(TEAM, DEPLOYED);

    verify(reach)
        .marked(
            new ReachLost(
                "notification",
                "team/deployed",
                List.of(),
                "team cannot tell deployed: no driver of kind teams"));
  }

  @Test
  void dropsThePostWhenTheDeliveryBacklogIsFull() {
    new ChannelPosts(
            List.of(driver),
            reach,
            task -> {
              throw new RejectedExecutionException("full");
            },
            retries)
        .post(TEAM, DEPLOYED);

    verify(driver, never()).deliver(any(), any());
  }

  @Test
  void logsOneWarnNotificationNotDeliveredNoMatterHowOftenItIsTried() {
    doThrow(new IllegalStateException("HTTP 500")).when(driver).deliver(any(), any());
    ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
    try (FakeLogCapture log = new FakeLogCapture(ChannelPosts.class)) {
      posts.post(TEAM, DEPLOYED);
      verify(retries).schedule(retry.capture(), eq(10L), eq(TimeUnit.SECONDS));
      assertThat(log.events())
          .filteredOn(event -> "notification not delivered".equals(event.getMessage()))
          .hasSize(1);
      retry.getValue().run();

      assertThat(log.events())
          .filteredOn(event -> "notification not delivered".equals(event.getMessage()))
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getKeyValuePairs())
                      .extracting(pair -> pair.key + "=" + pair.value)
                      .containsExactly(
                          "channel=team",
                          "point=deployed",
                          "task=ABC-42",
                          "cause=HTTP 500",
                          "effect=tried again until it lands"));
    }
  }

  @Test
  void warnsOnceInTotalWhenPostFailsAndBoardHeadStandsMarked() {
    doThrow(new IllegalStateException("HTTP 500")).when(driver).deliver(any(), any());
    ReachLedger ledger = new ReachLedger();
    try (FakeLogCapture log = new FakeLogCapture("dev.jagt.orchestrator")) {
      new ChannelPosts(List.of(driver), ledger, Runnable::run, retries).post(TEAM, DEPLOYED);

      assertThat(log.events())
          .filteredOn(event -> event.getLevel() == Level.WARN)
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("notification not delivered");
      assertThat(ledger.standing()).extracting(ReachLost::host).containsExactly("team/deployed");
    }
  }

  @Test
  void deliversTheNewerMessageWhenOneArrivesWhileTheOlderIsBeingDelivered() {
    ChannelMessage newer = new ChannelMessage("deployed", "ABC-42", "p3", "", "p3 is live again");
    doAnswer(
            call -> {
              posts.post(TEAM, newer);
              return null;
            })
        .doNothing()
        .when(driver)
        .deliver(any(), any());
    posts.post(TEAM, DEPLOYED);

    verify(driver).deliver(TEAM, newer);
  }
}
