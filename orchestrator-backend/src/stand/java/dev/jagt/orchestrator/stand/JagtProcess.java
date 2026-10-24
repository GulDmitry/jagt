package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class JagtProcess implements AutoCloseable {

    private static final Path JAR = Path.of("build", "libs", "jagt-run.jar").toAbsolutePath();
    private static final Duration STARTUP = Duration.ofMinutes(2);

    private final Process process;
    private final int port;
    private final StandBoard board;

    private JagtProcess(Process process, int port) {
        this.process = process;
        this.port = port;
        this.board = new StandBoard(port);
    }

    public static JagtProcess start(Path root) {
        int port = freePort();
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder builder = new ProcessBuilder(List.of(java, "-jar", JAR.toString(), "--server.port=" + port,
                "--orchestrator.open-terminal-window=false",
                "--logging.level.dev.jagt.orchestrator.surface.mcp.McpProtocolService=DEBUG",
                "--logging.level.dev.jagt.orchestrator.adapter.assistant.HeadlessClaude=DEBUG"))
                .directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(root.resolve("jagt.out").toFile());
        Map<String, String> env = builder.environment();
        List.of("TMUX", "TMUX_PANE", "ORCHESTRATOR_CONFIG_FILE", "ORCHESTRATOR_STATE_FILE", "LOG_FILE").forEach(env::remove);
        env.keySet().removeIf(name -> name.startsWith("CLAUDE"));
        env.put("ORCHESTRATOR_ROOT", root.toString());
        env.put("TMUX_TMPDIR", root.resolve("tmux").toString());
        try {
            return new JagtProcess(builder.start(), port).awaitReady(root);
        } catch (IOException e) {
            throw new StandFailure("cannot launch " + JAR + " under " + root, e);
        }
    }

    public long pid() {
        return process.pid();
    }

    public int port() {
        return port;
    }

    public StandBoard board() {
        return board;
    }

    @Override
    public void close() {
        StandShell.stopTree(process.toHandle());
    }

    private JagtProcess awaitReady(Path root) {
        Instant deadline = Instant.now().plus(STARTUP);
        while (!board.ready()) {
            if (!process.isAlive() || Instant.now().isAfter(deadline)) {
                close();
                throw new StandFailure("jagt did not come up on port " + port + "; read " + root.resolve("jagt.out"));
            }
            pause(Duration.ofSeconds(1));
        }
        return this;
    }

    static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StandFailure("interrupted", e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new StandFailure("no free port on loopback", e);
        }
    }
}
