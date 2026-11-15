package dev.jagt.orchestrator.surface.ui.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** The last lines its encoder wrote, kept where stdout is the only log. */
public class RecentLines extends OutputStreamAppender<ILoggingEvent> {

  static final int KEPT = 2000;

  private final Deque<String> lines = new ArrayDeque<>();

  @Override
  public void start() {
    setOutputStream(new Lines());
    super.start();
  }

  List<String> last(int count) {
    synchronized (lines) {
      return lines.stream().skip(Math.max(0, lines.size() - count)).toList();
    }
  }

  private final class Lines extends OutputStream {

    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    @Override
    public void write(int b) {
      if (b != '\n') {
        pending.write(b);
        return;
      }
      synchronized (lines) {
        lines.addLast(pending.toString(StandardCharsets.UTF_8));
        if (lines.size() > KEPT) {
          lines.removeFirst();
        }
      }
      pending.reset();
    }
  }
}
