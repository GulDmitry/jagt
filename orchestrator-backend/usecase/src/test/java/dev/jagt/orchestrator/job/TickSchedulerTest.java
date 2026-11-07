package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.port.ReadScope;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TickSchedulerTest {

  @Test
  void runsEveryScheduledRunWithinOneWindowRead() throws InterruptedException {
    ReadScope reads = mock(ReadScope.class);
    doAnswer(
            call -> {
              ((Runnable) call.getArgument(0)).run();
              return null;
            })
        .when(reads)
        .once(any());
    TickScheduler scheduler = new TickScheduler(reads);
    scheduler.setPoolSize(1);
    scheduler.initialize();
    CountDownLatch ran = new CountDownLatch(1);

    scheduler.schedule(
        ran::countDown, context -> context.lastActualExecution() == null ? Instant.now() : null);

    assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
    verify(reads).once(any());
    scheduler.shutdown();
  }
}
