package dev.jagt.orchestrator.adapter.terminal;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** What a window shows once its agent has exited, and how that is read back. */
public final class ExitedWindow {

  private static final String EXITED = "[jagt] agent exited";
  private static final int TAIL_LINES = 3;

  private ExitedWindow() {}

  /**
   * The shell line running {@code command}, then leaving a process-free window that shows why for
   * 15s: an interactive shell here would linger forever and read as a hung process.
   */
  public static String shellLine(String command) {
    return command
        + "; printf '\\n"
        + EXITED
        + " — window closes in 15s (Ctrl+C to close now)\\n'; exec sleep 15";
  }

  /** Whether the screen shows the marker an exited agent leaves. */
  public static boolean hasExited(String shown) {
    return shown.lines().anyMatch(line -> line.startsWith(EXITED));
  }

  /** The last lines printed before the exit marker; empty when the screen holds no marker. */
  public static Optional<String> printedBeforeExit(String shown) {
    List<String> lines = shown.lines().toList();
    int exited =
        IntStream.range(0, lines.size())
            .filter(i -> lines.get(i).startsWith(EXITED))
            .max()
            .orElse(-1);
    if (exited < 0) {
      return Optional.empty();
    }
    List<String> printed =
        lines.subList(0, exited).stream().filter(line -> !line.isBlank()).toList();
    return Optional.of(
        String.join(
            "\n", printed.subList(Math.max(0, printed.size() - TAIL_LINES), printed.size())));
  }
}
