package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.FutureTask;
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
    FutureTask<Thread> running = new FutureTask<>(Thread::currentThread);

    wiring.autoReviewSweeps().execute(running);

    Thread thread = running.get();
    assertThat(thread.getName()).isEqualTo("auto-review");
    assertThat(thread.isDaemon()).isTrue();
  }

  @Test
  void boardBroadcastsOnItsOwnDaemonThread() throws Exception {
    var broadcaster = wiring.boardBroadcaster();

    Thread thread = broadcaster.submit(Thread::currentThread).get();

    assertThat(thread.getName()).isEqualTo("board-sse");
    assertThat(thread.isDaemon()).isTrue();
    broadcaster.shutdownNow();
  }
}
