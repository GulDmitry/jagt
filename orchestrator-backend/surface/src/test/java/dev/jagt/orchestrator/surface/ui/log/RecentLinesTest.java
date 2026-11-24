package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecentLinesTest {

  private final LoggerContext context = new LoggerContext();

  @Test
  void keepsOnlyTheLastLinesAndNumbersTheWholeRun() {
    Logger logger = new TestRoleFeedBuilder(context, "master").build();
    IntStream.range(0, RecentLines.KEPT + 5).forEach(count -> logger.info("step"));
    RecentLines feed = (RecentLines) logger.getAppender("ROLE_master");

    RecentLines.Slice all = feed.after(0);
    RecentLines.Slice newest = feed.after(RecentLines.KEPT + 3);

    assertThat(all.lines()).hasSize(RecentLines.KEPT);
    assertThat(all.next()).isEqualTo(RecentLines.KEPT + 5);
    assertThat(newest.lines()).hasSize(2);
    assertThat(newest.next()).isEqualTo(RecentLines.KEPT + 5);
  }

  @Test
  void announcesEachLineWhenItIsWritten() {
    Logger logger = new TestRoleFeedBuilder(context, "master").build();
    RecentLines feed = (RecentLines) logger.getAppender("ROLE_master");
    Runnable announced = mock(Runnable.class);
    feed.onLine(announced);

    logger.info("one");
    logger.info("two");

    verify(announced, times(2)).run();
  }
}
