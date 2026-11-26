package dev.jagt.orchestrator.service.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThreadDumpTest {

  @Test
  void showsEveryLiveThreadWithItsStateAndWholeStack() {
    String dump = new ThreadDump().read();

    assertThat(dump)
        .contains("\"" + Thread.currentThread().getName() + "\" #")
        .contains("RUNNABLE")
        .contains("showsEveryLiveThreadWithItsStateAndWholeStack")
        .contains("    at ");
  }
}
