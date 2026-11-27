package dev.jagt.orchestrator.adapter.herdr;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.OneLine;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Views of a herdr window. herdr streams no output, so each view redraws the screen whenever it
 * differs from the last one it sent; the window keeps the size herdr's own client gives it.
 */
@Service
@ConditionalOnProperty(name = "orchestrator.multiplexer", havingValue = "herdr")
@RequiredArgsConstructor
@Slf4j
public class HerdrWindowStream implements WindowStream {

  private static final Duration REDRAW_EVERY = Duration.ofMillis(300);
  private static final String CLEAR = "\u001b[H\u001b[2J";

  private final HerdrApi api;
  private final HerdrReads reads;

  @Override
  public Optional<View> open(String session, String window, int columns, int rows, Sink sink) {
    return reads.windows(session).stream()
        .filter(listed -> listed.name().equals(window))
        .findFirst()
        .map(listed -> new Redrawn(session, listed.paneId(), sink));
  }

  private final class Redrawn implements View {

    private final String session;
    private final String paneId;
    private final Sink sink;
    private final CountDownLatch closed = new CountDownLatch(1);

    Redrawn(String session, String paneId, Sink sink) {
      this.session = session;
      this.paneId = paneId;
      this.sink = sink;
      Thread.ofVirtual().start(this::follow);
    }

    private void follow() {
      String sent = null;
      try {
        do {
          String screen = screen();
          if (!screen.equals(sent)) {
            sink.output((CLEAR + crlf(screen)).getBytes(StandardCharsets.UTF_8));
            sent = screen;
          }
        } while (!closed.await(REDRAW_EVERY.toMillis(), TimeUnit.MILLISECONDS));
      } catch (InterruptedException interrupted) {
        log.atDebug().setMessage("herdr view interrupted").addKeyValue("window", paneId).log();
        sink.ended("view interrupted");
        Thread.currentThread().interrupt();
      } catch (HerdrApi.Failed | IllegalStateException unreadable) {
        log.atWarn()
            .setMessage("herdr view ended")
            .addKeyValue("window", paneId)
            .addKeyValue("cause", OneLine.clipped(Causes.of(unreadable)))
            .setCause(unreadable)
            .log();
        sink.ended(String.valueOf(OneLine.clipped(Causes.of(unreadable))));
      }
    }

    private String crlf(String screen) {
      return screen.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    private String screen() {
      return api.call(
              session,
              "pane.read",
              Map.of("pane_id", paneId, "source", "visible", "format", "ansi", "strip_ansi", false))
          .path("read")
          .path("text")
          .asString("");
    }

    @Override
    public void type(String text) {
      api.call(session, "pane.send_text", Map.of("pane_id", paneId, "text", text));
    }

    @Override
    public void resize(int columns, int rows) {}

    @Override
    public void close() {
      closed.countDown();
    }
  }
}
