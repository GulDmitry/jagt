package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.ClaudeProperties;
import dev.jagt.orchestrator.port.Processes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the CLI says about its own MCP servers ({@code claude mcp list}, free and token-less) — the one thing a
 * read cannot report about itself: a model whose tool never loaded answers "does not exist" like a host whose
 * request is gone.
 *
 * <p>Empty = NOT established, which is a third answer and never "nothing is down": a probe that could not run
 * would otherwise clear the very failure it was called to explain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class McpHealthProbe {

    /** A human waits behind this on a refusal, and the probe measured ~9s here. */
    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    /** A warning line, not an inventory: an install with 20 servers must not push the read's url off screen. */
    private static final int MAX_LISTED = 8;
    /** Every probe starts every configured server, so asking again this soon costs seconds and learns nothing. */
    private static final Duration FRESH_FOR = Duration.ofMinutes(2);
    /** The CLI's word for a server that is up; every other verdict is one a read can trip over. */
    private static final String CONNECTED = "(Connected)";

    private final ProcessRunner processRunner;
    private final ClaudeProperties claude;
    private final AssistantProperties assistant;

    private Optional<List<String>> last = Optional.empty();
    private long lastAt;

    synchronized Optional<List<String>> brokenServers() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("Not asking which MCP servers are up: this thread is interrupted");
            return Optional.empty();
        }
        // `claude mcp list` takes neither --mcp-config nor --strict-mcp-config (verified 2026-08-24), so with a
        // declared set it would report the servers of a resolution the read never used.
        if (!assistant.mcpConfig().isBlank()) {
            log.warn("The read's MCP servers come from orchestrator.assistant.mcp-config, and the CLI cannot be"
                    + " asked about that set — which of them is down is NOT established");
            return Optional.empty();
        }
        if (lastAt != 0 && System.nanoTime() - lastAt < FRESH_FOR.toNanos()) {
            return last;
        }
        last = probe();
        lastAt = System.nanoTime();
        return last;
    }

    private Optional<List<String>> probe() {
        Processes.Result result;
        try {
            result = processRunner.run(Path.of(System.getProperty("java.io.tmpdir")), TIMEOUT,
                    List.of(claude.command(), "mcp", "list"));
        } catch (RuntimeException e) {
            log.error("Could not ask which MCP servers are up ({}), so a failed read cannot be explained: {}",
                    claude.command(), e.toString());
            return Optional.empty();
        }
        if (result.exitCode() != 0) {
            log.error("`{} mcp list` exited {}, so a failed read cannot be explained: {}", claude.command(),
                    result.exitCode(), result.stderr().isBlank() ? result.stdout() : result.stderr());
            return Optional.empty();
        }
        List<String> servers = result.stdout().lines()
                .map(McpHealthProbe::serverAndStatus)
                .flatMap(Optional::stream)
                .toList();
        // No line was a server line: either nothing is configured, or the output no longer looks like this.
        // Answering "nothing is down" here would clear the very failure the caller asked about.
        if (servers.isEmpty()) {
            log.error("`{} mcp list` named no server at all, so a failed read cannot be explained: {}",
                    claude.command(), result.stdout().strip());
            return Optional.empty();
        }
        List<String> broken = servers.stream().filter(server -> !server.endsWith(CONNECTED)).toList();
        if (broken.size() <= MAX_LISTED) {
            return Optional.of(broken);
        }
        List<String> capped = new ArrayList<>(broken.subList(0, MAX_LISTED));
        capped.add("and " + (broken.size() - MAX_LISTED) + " more");
        return Optional.of(capped);
    }

    /** A server line reads {@code <name>: <how it starts> - <marker> <verdict> — <detail>}, or it is not one. */
    private static Optional<String> serverAndStatus(String line) {
        int named = line.indexOf(": ");
        int verdictAt = markedVerdict(line);
        if (named < 0 || verdictAt < named) {
            return Optional.empty();
        }
        String verdict = line.substring(verdictAt + 3).trim();
        int detail = verdict.indexOf(" — ");
        String status = detail < 0 ? verdict : verdict.substring(0, detail);
        return Optional.of(line.substring(0, named) + " (" + status.trim().replaceFirst("^[^\\p{L}]+", "") + ")");
    }

    /**
     * The tick / cross / bang the CLI prints is what separates a server line from any other line carrying
     * {@code " - "} — including a failure detail that carries one itself.
     */
    private static int markedVerdict(String line) {
        for (int at = line.lastIndexOf(" - "); at >= 0; at = line.lastIndexOf(" - ", at - 1)) {
            String rest = line.substring(at + 3).stripLeading();
            if (!rest.isEmpty() && !Character.isLetterOrDigit(rest.charAt(0))) {
                return at;
            }
        }
        return -1;
    }
}
