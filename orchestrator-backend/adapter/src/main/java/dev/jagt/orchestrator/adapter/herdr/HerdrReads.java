package dev.jagt.orchestrator.adapter.herdr;

import dev.jagt.orchestrator.port.terminal.ReadScope;
import dev.jagt.orchestrator.port.terminal.SessionHost.WindowsUnreadable;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.OneLine;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * What herdr says about a session's windows; inside {@link #once} each session is listed once and
 * shared. A window is a tab labelled with its name, shown through that tab's first pane.
 */
@Service
@ConditionalOnProperty(name = "orchestrator.multiplexer", havingValue = "herdr")
@RequiredArgsConstructor
@Slf4j
public class HerdrReads implements ReadScope {

  public record Window(String tabId, String paneId, String name) {}

  private record Seen(String screen, long sinceMillis) {}

  private static final long START_WINDOW_MILLIS = 10_000;

  private static final class Tick {
    private long listedAt = -1;
    private final Map<String, List<Window>> windows = new HashMap<>();
    private final Map<String, WindowsUnreadable> failures = new HashMap<>();
  }

  private final HerdrApi api;
  private final Clock clock;
  private final AtomicLong generation = new AtomicLong();
  private final ScopedValue<Tick> tick = ScopedValue.newInstance();
  private final Map<String, Seen> seen = new ConcurrentHashMap<>();
  private final Map<String, Long> opened = new ConcurrentHashMap<>();

  @Override
  public void once(Runnable reads) {
    if (tick.isBound()) {
      reads.run();
      return;
    }
    ScopedValue.where(tick, new Tick()).run(reads);
  }

  /** A window opened, closed or pressed, on any thread, outdates what a tick has read. */
  public void changed() {
    generation.incrementAndGet();
  }

  /** A session or server that is not there lists no windows; any other failure is unknown. */
  public List<Window> windows(String session) {
    if (!tick.isBound()) {
      return list(session);
    }
    Tick scoped = tick.get();
    long current = generation.get();
    if (scoped.listedAt != current) {
      scoped.windows.clear();
      scoped.failures.clear();
      scoped.listedAt = current;
    }
    WindowsUnreadable failed = scoped.failures.get(session);
    if (failed != null) {
      throw failed;
    }
    List<Window> listed = scoped.windows.get(session);
    if (listed == null) {
      try {
        listed = list(session);
      } catch (WindowsUnreadable unreadable) {
        scoped.failures.put(session, unreadable);
        throw unreadable;
      }
      scoped.windows.put(session, listed);
    }
    return listed;
  }

  /** A window jagt just typed its line into is starting until its line shows. */
  public void started(Window window) {
    opened.put(window.paneId(), clock.millis());
  }

  public boolean starting(Window window) {
    Long at = opened.get(window.paneId());
    return at != null && clock.millis() - at < START_WINDOW_MILLIS;
  }

  /** What jagt kept of a window that ended. */
  public void forget(String session, String name, String paneId) {
    seen.remove(session + "/" + name);
    opened.remove(paneId);
  }

  public Optional<String> workspaceId(String session) {
    return api.tryCall(session, "workspace.list", Map.of())
        .flatMap(list -> labelled(list, session));
  }

  /**
   * Epoch millis jagt last saw the window's screen change; herdr reports none, so a screen met for
   * the first time counts from then. 0 once the pane is gone.
   */
  public long activityMillis(String session, Window window) {
    Optional<String> read =
        api.ifPaneThere(() ->
            api.call(session, "pane.read", Map.of("pane_id", window.paneId(), "source", "visible"))
                .path("read")
                .path("text")
                .asString(""));
    if (read.isEmpty()) {
      forget(session, window.name(), window.paneId());
      return 0;
    }
    String screen = read.get();
    return seen.compute(
            session + "/" + window.name(),
            (key, old) ->
                old != null && old.screen().equals(screen) ? old : new Seen(screen, clock.millis()))
        .sinceMillis();
  }

  private List<Window> list(String session) {
    try {
      return workspaceId(session).map(id -> windowsOf(session, id)).orElse(List.of());
    } catch (HerdrApi.Failed | IllegalStateException unreadable) {
      String cause = OneLine.clipped(Causes.of(unreadable));
      log.atWarn()
          .setMessage("herdr windows unreadable")
          .addKeyValue("session", session)
          .addKeyValue("cause", cause)
          .addKeyValue("effect", "no window opened, closed or reported this round")
          .setCause(unreadable)
          .log();
      throw new WindowsUnreadable("herdr windows of " + session + " unreadable: " + cause);
    }
  }

  private static Optional<String> labelled(JsonNode list, String session) {
    for (JsonNode workspace : list.path("workspaces")) {
      if (session.equals(workspace.path("label").asString(""))) {
        return Optional.of(workspace.path("workspace_id").asString(""));
      }
    }
    return Optional.empty();
  }

  private List<Window> windowsOf(String session, String workspaceId) {
    Map<String, String> params = Map.of("workspace_id", workspaceId);
    Map<String, String> firstPane = new HashMap<>();
    for (JsonNode pane : api.call(session, "pane.list", params).path("panes")) {
      firstPane.putIfAbsent(pane.path("tab_id").asString(""), pane.path("pane_id").asString(""));
    }
    List<Window> windows = new ArrayList<>();
    for (JsonNode tab : api.call(session, "tab.list", params).path("tabs")) {
      String tabId = tab.path("tab_id").asString("");
      if (firstPane.containsKey(tabId)) {
        windows.add(new Window(tabId, firstPane.get(tabId), tab.path("label").asString("")));
      }
    }
    return windows;
  }
}
