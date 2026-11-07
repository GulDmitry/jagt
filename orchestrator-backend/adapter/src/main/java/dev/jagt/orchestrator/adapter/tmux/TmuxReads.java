package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.ReadScope;
import dev.jagt.orchestrator.port.SessionHost.WindowsUnreadable;
import dev.jagt.orchestrator.service.OneLine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * What tmux and the process table say about windows; inside {@link #once} each is read once and
 * shared.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TmuxReads implements ReadScope {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final String FIELDS = "#{window_id} #{pane_pid} #{window_activity} #{window_name}";
  private static final Pattern SESSION_ABSENT =
      Pattern.compile("no server running|can't find session|error connecting to");

  public record Window(String id, String panePid, long activitySeconds, String name) {}

  private record Listed(String session, Window window) {}

  /** What one tick has read so far, each read stamped with the generation it saw. */
  private static final class Tick {
    private long listedAt = -1;
    private List<Listed> windows = List.of();
    private long parentsAt = -1;
    private Set<String> parents = Set.of();
  }

  private final Processes processes;
  private final ToolchainProperties properties;
  private final AtomicLong generation = new AtomicLong();
  private final ScopedValue<Tick> tick = ScopedValue.newInstance();

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

  public List<String> argv(String... args) {
    List<String> argv =
        new ArrayList<>(
            TmuxArgv.prefix(
                Executables.resolve(properties.tmuxCommand()), properties.tmuxSocket()));
    argv.addAll(List.of(args));
    return argv;
  }

  public List<Window> windows(String session) {
    Tick scoped = tick.isBound() ? tick.get() : null;
    if (scoped == null) {
      return lines(session, FIELDS).stream().flatMap(line -> window(line).stream()).toList();
    }
    long seen = generation.get();
    if (scoped.listedAt != seen) {
      scoped.windows =
          list(List.of("-a"), "#{session_name}\t" + FIELDS, "*").stream()
              .map(line -> line.split("\t", 2))
              .filter(parts -> parts.length == 2)
              .flatMap(
                  parts -> window(parts[1]).map(window -> new Listed(parts[0], window)).stream())
              .toList();
      scoped.listedAt = seen;
    }
    return scoped.windows.stream()
        .filter(listed -> listed.session().equals(session))
        .map(Listed::window)
        .toList();
  }

  public boolean hasChildren(String panePid) {
    Tick scoped = tick.isBound() ? tick.get() : null;
    if (scoped == null) {
      var children = processes.run(null, TIMEOUT, List.of("pgrep", "-P", panePid));
      return children.exitCode() == 0 && !children.stdout().isBlank();
    }
    long seen = generation.get();
    if (scoped.parentsAt != seen) {
      scoped.parents = parents();
      scoped.parentsAt = seen;
    }
    return scoped.parents.contains(panePid);
  }

  public List<String> lines(String session, String format) {
    return list(List.of("-t", "=" + session), format, session);
  }

  /**
   * A session or server that is not there lists no windows; any other failure is unknown, never
   * "none".
   */
  private List<String> list(List<String> target, String format, String shown) {
    List<String> args = new ArrayList<>(List.of("list-windows"));
    args.addAll(target);
    args.addAll(List.of("-F", format));
    var windows = processes.run(null, TIMEOUT, argv(args.toArray(String[]::new)));
    if (windows.exitCode() == 0) {
      return windows.stdout().lines().toList();
    }
    if (SESSION_ABSENT.matcher(windows.stderr()).find()) {
      return List.of();
    }
    log.atWarn()
        .setMessage("tmux windows unreadable")
        .addKeyValue("session", shown)
        .addKeyValue("cause", OneLine.clipped(windows.stderr()))
        .addKeyValue("effect", "no window opened, closed or reported this round")
        .log();
    throw new WindowsUnreadable(
        "tmux list-windows "
            + shown
            + " failed (exit "
            + windows.exitCode()
            + "): "
            + OneLine.clipped(windows.stderr()));
  }

  private Set<String> parents() {
    var table = processes.run(null, TIMEOUT, List.of("ps", "-A", "-o", "ppid="));
    if (table.exitCode() != 0) {
      log.atWarn()
          .setMessage("process table unreadable")
          .addKeyValue("cause", OneLine.clipped(table.stderr()))
          .addKeyValue("effect", "no window reported dead or alive this round")
          .log();
      throw new WindowsUnreadable(
          "ps failed (exit " + table.exitCode() + "): " + OneLine.clipped(table.stderr()));
    }
    return table.stdout().lines().map(String::trim).collect(Collectors.toSet());
  }

  private static Optional<Window> window(String line) {
    String[] fields = line.split(" ", 4);
    return fields.length < 4
        ? Optional.empty()
        : Optional.of(new Window(fields[0], fields[1], Long.parseLong(fields[2]), fields[3]));
  }
}
