package dev.jagt.orchestrator.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.tmux.ResizedWindows;
import dev.jagt.orchestrator.adapter.tmux.TmuxReads;
import dev.jagt.orchestrator.adapter.tmux.TmuxWindowStream;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.WindowStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TmuxWindowStreamE2eTest {

  private static final String SESSION =
      "jagt-stream-e2e-" + UUID.randomUUID().toString().substring(0, 8);
  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

  private final Processes processes = new ProcessRunner();
  private final TmuxReads reads =
      new TmuxReads(processes, ToolchainProperties.defaults().withTmuxCommand("tmux"));
  private final WindowStream stream =
      new TmuxWindowStream(processes, reads, new ResizedWindows(processes, reads));
  private final List<String> askWindowSize =
      reads.argv(
          "display-message",
          "-p",
          "-t",
          "=" + SESSION + ":ABC-1",
          "#{window_width}x#{window_height}");

  @BeforeEach
  void startSessionWithOneWindowEchoingWhatItIsTyped() {
    processes
        .run(
            null, TEN_SECONDS, reads.argv("new-session", "-d", "-s", SESSION, "-n", "ABC-1", "cat"))
        .expectSuccess("tmux new-session");
  }

  @AfterEach
  void killTheThrowawaySession() {
    processes.run(null, TEN_SECONDS, reads.argv("kill-session", "-t", "=" + SESSION));
  }

  @Test
  void streamsWhatIsTypedBackAndGivesTheWindowItsSizeBackOnClose() {
    final String before = processes.run(null, TEN_SECONDS, askWindowSize).stdout();
    FakePrintedSink printed = new FakePrintedSink();

    WindowStream.View view = stream.open(SESSION, "ABC-1", 100, 30, printed).orElseThrow();
    view.type("hello\r");
    await()
        .atMost(TEN_SECONDS)
        .untilAsserted(() -> assertThat(printed.printed()).contains("hello\r\nhello"));
    String during = processes.run(null, TEN_SECONDS, askWindowSize).stdout();
    view.close();

    assertThat(during).isEqualTo("100x30");
    assertThat(processes.run(null, TEN_SECONDS, askWindowSize).stdout()).isEqualTo(before);
  }

  @Test
  void opensNoViewOfWindowTheSessionDoesNotHold() {
    assertThat(stream.open(SESSION, "ABC-2", 100, 30, new FakePrintedSink())).isEmpty();
  }
}
