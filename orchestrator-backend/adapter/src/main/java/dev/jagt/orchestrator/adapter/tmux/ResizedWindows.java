package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.OneLine;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Windows whose size a view holds: the first view to claim one records what it was, the last to
 * release it puts that back.
 */
@Component
@ConditionalOnProperty(
    name = "orchestrator.multiplexer",
    havingValue = "tmux",
    matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ResizedWindows {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private record Before(String columns, String rows, String policy, int views) {

    Before withViews(int count) {
      return new Before(columns, rows, policy, count);
    }
  }

  private final Processes processes;
  private final TmuxReads reads;
  private final Map<String, Before> held = new HashMap<>();

  synchronized void claim(String windowId, int columns, int rows) {
    Before before = held.get(windowId);
    held.put(windowId, before == null ? recorded(windowId) : before.withViews(before.views() + 1));
    resize(windowId, columns, rows);
  }

  synchronized void resize(String windowId, int columns, int rows) {
    run(
        "resize",
        windowId,
        "resize-window",
        "-t",
        windowId,
        "-x",
        String.valueOf(columns),
        "-y",
        String.valueOf(rows));
  }

  synchronized void release(String windowId) {
    Before before = held.get(windowId);
    if (before == null) {
      return;
    }
    if (before.views() > 1) {
      held.put(windowId, before.withViews(before.views() - 1));
      return;
    }
    held.remove(windowId);
    resize(windowId, Integer.parseInt(before.columns()), Integer.parseInt(before.rows()));
    if (before.policy().isBlank()) {
      run("restore", windowId, "set-option", "-w", "-u", "-t", windowId, "window-size");
    } else {
      run("restore", windowId, "set-option", "-w", "-t", windowId, "window-size", before.policy());
    }
  }

  private Before recorded(String windowId) {
    String size =
        processes
            .run(
                null,
                TIMEOUT,
                reads.argv(
                    "display-message", "-p", "-t", windowId, "#{window_width} #{window_height}"))
            .expectSuccess("tmux display-message " + windowId)
            .stdout()
            .trim();
    String policy =
        processes
            .run(
                null,
                TIMEOUT,
                reads.argv("show-options", "-w", "-q", "-v", "-t", windowId, "window-size"))
            .stdout()
            .trim();
    String[] parts = size.split(" ", 2);
    return new Before(parts[0], parts[1], policy, 1);
  }

  private void run(String what, String windowId, String... args) {
    var result = processes.run(null, TIMEOUT, reads.argv(args));
    if (result.exitCode() != 0) {
      log.atWarn()
          .setMessage("tmux window size not set")
          .addKeyValue("window", windowId)
          .addKeyValue("step", what)
          .addKeyValue("cause", OneLine.clipped(result.stderr()))
          .addKeyValue("effect", "the window keeps the size it had")
          .log();
    }
  }
}
