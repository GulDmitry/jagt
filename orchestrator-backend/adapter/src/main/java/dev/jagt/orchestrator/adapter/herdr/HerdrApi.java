package dev.jagt.orchestrator.adapter.herdr;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.SessionHost.WindowsUnreadable;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.OneLine;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** herdr's JSON API as one call at a time, one socket per named session. */
@Service
@ConditionalOnProperty(name = "orchestrator.session-host", havingValue = "herdr")
@RequiredArgsConstructor
@Slf4j
public class HerdrApi {

  private static final String PANE_GONE = "pane_not_found";

  private static final Duration ANSWER_WITHIN = Duration.ofSeconds(20);
  private static final int START_TRIES = 100;
  private static final Duration START_POLL = Duration.ofMillis(100);

  /** The server answered with an error of its own. */
  public static final class Failed extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    Failed(String code, String message) {
      super(code + ": " + message);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  private final Host host;
  private final HerdrProperties properties;
  private final Processes processes;
  private final HerdrWire wire;
  private final JsonMapper json = JsonMapper.builder().build();

  /** The herdr CLI for the named session in the configured home, its arguments after. */
  public List<String> argv(String session, String... args) {
    List<String> argv = inHome();
    argv.addAll(List.of(Executables.resolve(properties.command()), "--session", session));
    argv.addAll(List.of(args));
    return argv;
  }

  /** The argv showing the session on a screen. */
  public List<String> attachCommand(String session) {
    List<String> argv = inHome();
    argv.addAll(List.of(Executables.resolve(properties.command()), "session", "attach", session));
    return argv;
  }

  /** Whether a client of the session is running on this machine, as the process table shows. */
  public boolean viewerAttached(String session) {
    var table = processes.run(null, ANSWER_WITHIN, List.of("ps", "-A", "-o", "command="));
    if (table.exitCode() != 0) {
      throw new IllegalStateException("ps failed (exit " + table.exitCode() + ")");
    }
    List<String> attach = List.of("session", "attach", session);
    return table
        .stdout()
        .lines()
        .map(line -> List.of(line.trim().split("\\s+")))
        .anyMatch(words -> Collections.indexOfSubList(words, attach) >= 0);
  }

  /**
   * The answer's {@code result}, empty where no server listens; a {@link Failed} where the server
   * refuses, an {@link IllegalStateException} where it cannot be read.
   */
  public Optional<JsonNode> tryCall(String session, String method, Map<String, ?> params) {
    try {
      String request =
          json.writeValueAsString(Map.of("id", "jagt", "method", method, "params", params));
      Optional<String> reply = wire.exchange(socket(session), request);
      return reply.isEmpty() ? Optional.empty() : result(json.readTree(reply.get()));
    } catch (IOException | JacksonException unreadable) {
      throw new IllegalStateException(
          "herdr " + method + " failed: " + unreadable.getMessage(), unreadable);
    }
  }

  /** As {@link #tryCall}, for a server that must be there. */
  public JsonNode call(String session, String method, Map<String, ?> params) {
    return tryCall(session, method, params)
        .orElseThrow(
            () -> new IllegalStateException("herdr " + method + ": no server for " + session));
  }

  /**
   * What the action yields, empty where its pane is gone; a {@link WindowsUnreadable} where herdr
   * fails any other way.
   */
  public <T> Optional<T> ifPaneThere(Supplier<T> action) {
    try {
      return Optional.of(action.get());
    } catch (WindowsUnreadable unreadable) {
      throw unreadable;
    } catch (Failed failed) {
      if (PANE_GONE.equals(failed.code())) {
        return Optional.empty();
      }
      throw unreadable(failed);
    } catch (IllegalStateException failed) {
      throw unreadable(failed);
    }
  }

  private static WindowsUnreadable unreadable(RuntimeException failed) {
    String cause = OneLine.clipped(Causes.of(failed));
    log.atWarn()
        .setMessage("herdr pane unreadable")
        .addKeyValue("cause", cause)
        .addKeyValue("effect", "the pane decides nothing this round")
        .setCause(failed)
        .log();
    return new WindowsUnreadable("herdr pane unreadable: " + cause);
  }

  /** Starts the session's server where none runs, outliving jagt, and waits until it answers. */
  public void ensureServer(String session) {
    if (tryCall(session, "ping", Map.of()).isPresent()) {
      return;
    }
    Process server = processes.runDetached(null, argv(session, "server"));
    try {
      for (int tries = 0; tries < START_TRIES; tries++) {
        if (server.waitFor(START_POLL.toMillis(), TimeUnit.MILLISECONDS)) {
          throw new IllegalStateException("herdr server for " + session + " exited at start");
        }
        if (tryCall(session, "ping", Map.of()).isPresent()) {
          return;
        }
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while starting herdr", interrupted);
    }
    throw new IllegalStateException("herdr server for " + session + " did not answer");
  }

  private Optional<JsonNode> result(JsonNode answer) {
    JsonNode error = answer.path("error");
    if (!error.isObject()) {
      return Optional.of(answer.path("result"));
    }
    String code = error.path("code").asString("");
    if ("server_not_running".equals(code)) {
      return Optional.empty();
    }
    throw new Failed(code, error.path("message").asString(""));
  }

  Path socket(String session) {
    Path home = override().orElseGet(() -> host.home().resolve(".config").resolve("herdr"));
    return home.resolve("sessions").resolve(session).resolve("herdr.sock");
  }

  private Optional<Path> override() {
    String configured = properties.home();
    if (configured.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        configured.startsWith("~/")
            ? host.home().resolve(configured.substring(2))
            : Path.of(configured));
  }

  private List<String> inHome() {
    List<String> argv = new ArrayList<>();
    override()
        .ifPresent(home -> argv.addAll(List.of("env", "XDG_CONFIG_HOME=" + home.getParent())));
    return argv;
  }
}
