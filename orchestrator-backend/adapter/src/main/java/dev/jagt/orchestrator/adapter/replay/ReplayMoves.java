package dev.jagt.orchestrator.adapter.replay;

import dev.jagt.orchestrator.adapter.agent.McpEndpoint;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.WorktreeHooks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Makes a recorded move as the session made it: its files, its git, its MCP call through the sessions' own door. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@RequiredArgsConstructor
@Slf4j
public class ReplayMoves {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Duration GIT = Duration.ofMinutes(1);
    private static final Duration CALL = Duration.ofMinutes(2);

    private final McpEndpoint mcp;
    private final Processes processes;
    private final HttpClient http = HttpClient.newHttpClient();

    public void make(Path worktree, RecordingLine.Move move) {
        switch (move) {
            case RecordingLine.Called called -> call(worktree, called);
            case RecordingLine.Wrote wrote -> wrote.files().forEach((path, content) -> write(worktree.resolve(path), content));
            case RecordingLine.Pushed pushed -> push(worktree, pushed);
        }
    }

    private void call(Path worktree, RecordingLine.Called called) {
        ObjectNode message = JSON.createObjectNode().put("jsonrpc", "2.0").put("id", called.turn())
                .put("method", "tools/call");
        message.putObject("params").put("name", called.tool()).set("arguments", called.arguments());
        HttpRequest request = HttpRequest.newBuilder(URI.create(mcp.url())).timeout(CALL)
                .header("Content-Type", "application/json")
                .header(McpEndpoint.CALLER_HEADER, McpEndpoint.callerHeaderValue(worktree))
                .POST(HttpRequest.BodyPublishers.ofString(message.toString())).build();
        try {
            HttpResponse<String> answer = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.atInfo().setMessage("replay move made").addKeyValue("tool", called.tool())
                    .addKeyValue("status", answer.statusCode()).log();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot reach " + mcp.url(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted calling " + called.tool(), e);
        }
    }

    private void push(Path worktree, RecordingLine.Pushed pushed) {
        List<String> add = new ArrayList<>(List.of("git", "add", "--"));
        add.addAll(pushed.paths());
        git(worktree, add);
        git(worktree, List.of("git", "commit", "-m", pushed.message()));
        git(worktree, List.of("git", "push", "-u", "origin", "HEAD"));
    }

    private void git(Path worktree, List<String> command) {
        Map<String, String> env = WorktreeHooks.gitEnvironment(worktree);
        processes.run(worktree, GIT, env, command).expectSuccess(String.join(" ", command));
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
