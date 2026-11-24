package dev.jagt.orchestrator.surface.ui.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The last lines its encoder wrote, numbered, each one announced as it is written. */
public class RecentLines extends OutputStreamAppender<ILoggingEvent> {

  static final int KEPT = 2000;

  /** The lines written after a cursor, and the cursor that follows the last of them. */
  public record Slice(List<String> lines, long next) {}

  private final Deque<String> lines = new ArrayDeque<>();
  private long written;
  private volatile @Nullable Runnable onLine;

  @Override
  public void start() {
    setOutputStream(new Lines());
    super.start();
  }

  void onLine(Runnable listener) {
    onLine = listener;
  }

  List<String> last(int count) {
    synchronized (lines) {
      return lines.stream().skip(Math.max(0, lines.size() - count)).toList();
    }
  }

  Slice after(long cursor) {
    synchronized (lines) {
      long dropped = written - lines.size();
      return new Slice(lines.stream().skip(Math.max(0, cursor - dropped)).toList(), written);
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
        written++;
        if (lines.size() > KEPT) {
          lines.removeFirst();
        }
      }
      pending.reset();
      Runnable listener = onLine;
      if (listener != null) {
        listener.run();
      }
    }
  }
}
