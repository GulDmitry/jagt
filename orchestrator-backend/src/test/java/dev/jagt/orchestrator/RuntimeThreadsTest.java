package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RuntimeThreadsTest {

  private final RuntimeWiring wiring = new RuntimeWiring();

  @Test
  void notificationRetriesRunOnTheirOwnDaemonThread() throws Exception {
    var retries = wiring.notificationRetries();

    Thread thread = retries.schedule(Thread::currentThread, 0, TimeUnit.SECONDS).get();

    assertThat(thread.getName()).isEqualTo("jagt-notify-retry");
    assertThat(thread.isDaemon()).isTrue();
    retries.shutdownNow();
  }

  @Test
  void autoReviewSweepsRunOnTheirOwnDaemonThread() throws Exception {
    ExecutorService sweeps = (ExecutorService) wiring.autoReviewSweeps(); // allowed: 0237

    Thread thread = sweeps.submit(Thread::currentThread).get();

    assertThat(thread.getName()).isEqualTo("auto-review");
    assertThat(thread.isDaemon()).isTrue();
    sweeps.shutdownNow();
  }

  @Test
  void boardBroadcastsOnItsOwnDaemonThread() throws Exception {
    ExecutorService broadcaster = wiring.boardBroadcaster(); // allowed: 0237

    Thread thread = broadcaster.submit(Thread::currentThread).get();

    assertThat(thread.getName()).isEqualTo("board-sse");
    assertThat(thread.isDaemon()).isTrue();
    broadcaster.shutdownNow();
  }
}
