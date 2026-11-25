package dev.jagt.orchestrator.adapter.herdr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.FakeSink;
import dev.jagt.orchestrator.adapter.ProcessRunnerTest;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.testing.FakeHost;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HerdrWindowStreamTest {

  private final FakeHerdrWire herdr = new FakeHerdrWire();
  private final HerdrApi api =
      new HerdrApi(
          FakeHost.machine(),
          new HerdrProperties("/bin/herdr", "/state/herdr"),
          mock(Processes.class),
          herdr);
  private final HerdrWindowStream stream =
      new HerdrWindowStream(api, new HerdrReads(api, mock(Clock.class)));

  HerdrWindowStreamTest() throws Exception {}

  @Test
  void deliversTheScreenAsItIsWhenTheViewOpens() throws Exception {
    herdr.withTab("ABC-1", true).showing("ABC-1", "hello");
    FakeSink sink = new FakeSink();

    Optional<WindowStream.View> view = stream.open("jagt", "ABC-1", 80, 24, sink);

    assertThat(sink.next()).endsWith("hello");
    view.orElseThrow().close();
  }

  @Test
  void redrawsTheScreenOnceItChanges() throws Exception {
    herdr.withTab("ABC-1", true).showing("ABC-1", "first");
    FakeSink sink = new FakeSink();
    final WindowStream.View view = stream.open("jagt", "ABC-1", 80, 24, sink).orElseThrow();
    sink.next();
    herdr.showing("ABC-1", "second");

    assertThat(sink.next()).endsWith("second");
    view.close();
  }

  @Test
  void typesIntoTheWindowAsHumansDo() throws Exception {
    herdr.withTab("ABC-1", true);
    WindowStream.View view = stream.open("jagt", "ABC-1", 80, 24, new FakeSink()).orElseThrow();

    view.type("ls");
    view.close();

    assertThat(herdr.screenOf("ABC-1")).endsWith("ls");
  }

  @Test
  void endsTheViewNamingWhyWhereTheScreenCannotBeRead() {
    herdr.withTab("ABC-1", true).refusing("pane.read", "pane_not_found");
    FakeSink sink = new FakeSink();

    stream.open("jagt", "ABC-1", 80, 24, sink);

    await().atMost(ProcessRunnerTest.WAIT_CEILING).until(() -> sink.endedWith() != null);
    assertThat(sink.endedWith()).contains("pane_not_found");
  }

  @Test
  void redrawsLineFeedsAsCarriageReturnLineFeeds() throws Exception {
    herdr.withTab("ABC-1", true).showing("ABC-1", "one\ntwo");
    FakeSink sink = new FakeSink();

    WindowStream.View view = stream.open("jagt", "ABC-1", 80, 24, sink).orElseThrow();

    assertThat(sink.next()).endsWith("one\r\ntwo");
    view.close();
  }

  @Test
  void endsTheViewWhereItsThreadIsInterrupted() {
    herdr.withTab("ABC-1", true).showing("ABC-1", "x");
    AtomicReference<String> ended = new AtomicReference<>();

    stream.open(
        "jagt",
        "ABC-1",
        80,
        24,
        new WindowStream.Sink() {
          @Override
          public void output(byte[] bytes) {
            Thread.currentThread().interrupt();
          }

          @Override
          public void ended(String cause) {
            ended.set(cause);
          }
        });

    await().atMost(ProcessRunnerTest.WAIT_CEILING).until(() -> ended.get() != null);
    assertThat(ended.get()).contains("interrupted");
  }

  @Test
  void opensNoViewOfWindowsTheSessionDoesNotHold() {
    herdr.withTab("ABC-2", true);

    assertThat(stream.open("jagt", "ABC-1", 80, 24, new FakeSink())).isEmpty();
  }
}
