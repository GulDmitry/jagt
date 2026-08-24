package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.WebTerminalProperties;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.port.WebTerminal;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

/**
 * Serves a tmux session over HTTP with ttyd. It never selects a window — which task is on screen stays tmux's
 * own answer.
 *
 * <p>The terminal is WRITABLE (ttyd's default is not): the point of looking at an agent is answering it — which
 * makes typing into it equal to a shell on this machine. A bound address is not enough to protect that, since a
 * websocket handshake is exempt from same-origin rules and any page open in the human's browser can attempt one
 * against loopback; the served page is the only allowed origin.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TtydWebTerminal implements WebTerminal, StartupCheck {

    /** Long enough to catch a server that cannot bind, short enough not to be felt on the first click. */
    private static final Duration START_GRACE = Duration.ofMillis(400);
    private static final int PORTS_TRIED = 20;

    private final ProcessRunner processRunner;
    private final WebTerminalProperties properties;
    private final OrchestratorProperties orchestrator;
    private final Map<String, Server> servers = new LinkedHashMap<>();

    private record Server(int port, Process process) {
    }

    @Override
    public List<String> problems() {
        return properties.enabled() && Executables.unresolved(Executables.resolve(properties.command()))
                ? List.of("orchestrator.web-terminal.command: '" + properties.command() + "' is not on PATH"
                        + " nor in the usual install directories, and the web terminal is enabled. Install"
                        + " ttyd, set the key to a full path, or turn the terminal off.")
                : List.of();
    }

    /**
     * Empty when no web terminal is configured — the caller then has nothing to show. The HOST is deliberately
     * not answered: only the browser knows the name it reached jagt under, and the server it must ask for the
     * terminal is the same machine.
     *
     * @throws IllegalStateException when the server cannot be launched at all, e.g. no ttyd installed
     */
    @Override
    public synchronized OptionalInt serve(String tmuxSession) {
        if (!properties.enabled()) {
            return OptionalInt.empty();
        }
        Server running = servers.get(tmuxSession);
        if (running != null && running.process().isAlive()) {
            return OptionalInt.of(running.port());
        }
        Optional<Server> started = start(tmuxSession);
        started.ifPresent(server -> servers.put(tmuxSession, server));
        return started.map(server -> OptionalInt.of(server.port())).orElseGet(OptionalInt::empty);
    }

    @PreDestroy
    synchronized void stopServers() {
        servers.values().forEach(server -> server.process().destroy());
        servers.clear();
    }

    private Optional<Server> start(String tmuxSession) {
        int port = firstFreePortFrom(properties.port());
        List<String> command = serveCommand(Executables.resolve(properties.command()), properties.bind(), port,
                orchestrator.tmuxCommand(), tmuxSession);
        Process process;
        try {
            process = processRunner.runDetached(null, command);
        } catch (RuntimeException notLaunched) {
            log.atWarn().setMessage("web terminal start failed")
                    .addKeyValue("session", tmuxSession)
                    .addKeyValue("cause", notLaunched.getMessage())
                    .addKeyValue("cmd", String.join(" ", command))
                    .log();
            throw notLaunched;
        }
        if (exited(process)) {
            log.atWarn().setMessage("web terminal exited at once")
                    .addKeyValue("session", tmuxSession)
                    .addKeyValue("exit", process.exitValue())
                    .addKeyValue("cmd", String.join(" ", command))
                    .log();
            return Optional.empty();
        }
        return Optional.of(new Server(port, process));
    }

    private static boolean exited(Process process) {
        try {
            return process.waitFor(START_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * The next port nobody is listening on, so a server left behind by a killed jagt moves the new one along
     * instead of making every Focus answer with nothing. Falling back to {@code first} keeps the failure named.
     */
    private static int firstFreePortFrom(int first) {
        for (int port = first; port < first + PORTS_TRIED; port++) {
            try (ServerSocket probe = new ServerSocket(port)) {
                return probe.getLocalPort();
            } catch (IOException taken) {
                log.atDebug().setMessage("web terminal port taken")
                        .addKeyValue("port", port)
                        .log();
            }
        }
        return first;
    }

    /** The argv, so the flags stay testable on a machine that does not have ttyd. */
    static List<String> serveCommand(String ttydCommand, String bind, int port, String tmuxCommand,
                                     String tmuxSession) {
        List<String> command = new ArrayList<>(List.of(ttydCommand, "--port", String.valueOf(port), "--writable",
                "--check-origin",
                // Nothing is left running once the panel is closed, whatever became of the tmux session.
                "--exit-no-conn",
                // Closing the frame otherwise asks the human whether they mean to end the session.
                "--client-option", "disableLeaveAlert=true"));
        if (bind != null && !bind.isBlank()) {
            command.addAll(List.of("--interface", bind.strip()));
        }
        command.addAll(List.of(tmuxCommand, "attach", "-t", tmuxSession));
        return List.copyOf(command);
    }
}
