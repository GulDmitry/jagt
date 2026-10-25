package dev.jagt.orchestrator.adapter.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import dev.jagt.orchestrator.adapter.agent.McpEndpoint;
import dev.jagt.orchestrator.port.Processes;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

@ResourceLock("loopback-ports")
@Execution(ExecutionMode.SAME_THREAD)
class ReplayMovesTest {

    @Test
    void aReplayWritesTheFilesTheSessionLeftInItsTreeCreatingTheirFolders(@TempDir Path worktree) throws Exception {
        ReplayMoves moves = new ReplayMoves(new McpEndpoint("http://127.0.0.1:1/mcp"), mock(Processes.class));

        moves.make(worktree, new RecordingLine.Wrote("ABC-1", 1, Map.of("src/widget.txt", "done")));

        assertThat(Files.readString(worktree.resolve("src/widget.txt"))).isEqualTo("done");
    }

    @Test
    void aReplayMakesTheSessionsMcpCallAsTheWorktreeItWasRecordedIn(@TempDir Path worktree) throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> caller = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/mcp", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes()));
            caller.set(exchange.getRequestHeaders().getFirst(McpEndpoint.CALLER_HEADER));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            ReplayMoves moves = new ReplayMoves(
                    new McpEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"), mock(Processes.class));

            moves.make(worktree, new RecordingLine.Called("ABC-1", 7, "update_agent_status",
                    JsonMapper.builder().build().createObjectNode().put("status", "done")));

            assertThat(caller.get()).isEqualTo(McpEndpoint.callerHeaderValue(worktree));
            assertThat(JsonMapper.builder().build().readTree(body.get()).path("params").path("name").asString())
                    .isEqualTo("update_agent_status");
            assertThat(JsonMapper.builder().build().readTree(body.get()).path("params").path("arguments")
                    .path("status").asString()).isEqualTo("done");
            assertThat(JsonMapper.builder().build().readTree(body.get()).path("id").asLong()).isEqualTo(7);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aReplayInterruptedMidCallStaysInterruptedAndStopsTheReplay(@TempDir Path worktree) {
        ReplayMoves moves = new ReplayMoves(new McpEndpoint("http://127.0.0.1:1/mcp"), mock(Processes.class));
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> moves.make(worktree, new RecordingLine.Called("ABC-1", 1, "update_agent_status",
                JsonMapper.builder().build().createObjectNode()))).isInstanceOf(IllegalStateException.class);

        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void aReplayCommitsTheRecordedPathsThenPushesTheTaskBranch(@TempDir Path worktree) {
        Processes processes = mock(Processes.class);
        when(processes.run(any(Path.class), any(Duration.class), anyMap(), anyList()))
                .thenReturn(new Processes.Result(0, "", ""));
        ReplayMoves moves = new ReplayMoves(new McpEndpoint("http://127.0.0.1:1/mcp"), processes);

        moves.make(worktree, new RecordingLine.Pushed("ABC-1", 1, "Fix the widget", List.of("a.txt", "b.txt")));

        InOrder order = inOrder(processes);
        order.verify(processes).run(any(Path.class), any(Duration.class), anyMap(),
                org.mockito.ArgumentMatchers.eq(List.of("git", "add", "--", "a.txt", "b.txt")));
        order.verify(processes).run(any(Path.class), any(Duration.class), anyMap(),
                org.mockito.ArgumentMatchers.eq(List.of("git", "commit", "-m", "Fix the widget")));
        order.verify(processes).run(any(Path.class), any(Duration.class), anyMap(),
                org.mockito.ArgumentMatchers.eq(List.of("git", "push", "-u", "origin", "HEAD")));
    }
}
